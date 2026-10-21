package tech.kayys.numou.identity.adapter.inbound.grpc;

import io.quarkus.grpc.GrpcService;
import io.quarkus.grpc.RegisterInterceptor;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import tech.kayys.numou.identity.application.service.PersonalAccessTokenService;
import tech.kayys.numou.identity.application.service.ScopedRoleAssignmentService;
import tech.kayys.numou.identity.application.service.TokenService;
import tech.kayys.numou.identity.grpc.AuthCheckService;
import tech.kayys.numou.identity.grpc.CheckPermissionRequest;
import tech.kayys.numou.identity.grpc.CheckPermissionResponse;
import tech.kayys.numou.identity.grpc.CheckPermissionsRequest;
import tech.kayys.numou.identity.grpc.CheckPermissionsResponse;
import tech.kayys.numou.identity.grpc.PathPermissionPair;
import tech.kayys.numou.identity.grpc.PrincipalType;
import tech.kayys.numou.identity.grpc.ResolvePrincipalRequest;
import tech.kayys.numou.identity.grpc.ResolvePrincipalResponse;
import tech.kayys.numou.observability.MetricName;
import tech.kayys.numou.observability.MetricsPort;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implements the {@code AuthCheckService} gRPC contract.
 * Every check fails closed: any unexpected error results in allowed = false / valid = false.
 *
 * <p>Auth decisions are recorded on the framework {@link MetricsPort}:
 * {@code identity.auth.decision.time}, {@code identity.auth.decision.result} and
 * {@code identity.token.validation}. Tag values are bounded enums (outcome, principal type,
 * token type) so the metrics never blow up time-series cardinality; per-principal detail
 * belongs on traces/logs, carried by {@code traceId}.
 */
@GrpcService
@RegisterInterceptor(TraceContextServerInterceptor.class)
public class AuthCheckServiceImpl implements AuthCheckService {

    private static final MetricsPort NOOP_METRICS = new MetricsPort() {
        @Override
        public void increment(MetricName name, long amount) { }

        @Override
        public void record(MetricName name, Duration duration) { }

        @Override
        public void gauge(MetricName name, java.util.function.Supplier<Number> value) { }
    };

    private final ScopedRoleAssignmentService roleAssignmentService;
    private final PersonalAccessTokenService personalAccessTokenService;
    private final TokenService tokenService;
    private final MetricsPort metrics;

    @Inject
    public AuthCheckServiceImpl(
            ScopedRoleAssignmentService roleAssignmentService,
            PersonalAccessTokenService personalAccessTokenService,
            TokenService tokenService,
            MetricsPort metrics
    ) {
        this.roleAssignmentService = roleAssignmentService;
        this.personalAccessTokenService = personalAccessTokenService;
        this.tokenService = tokenService;
        this.metrics = metrics != null ? metrics : NOOP_METRICS;
    }

    /** Test/legacy constructor without metrics instrumentation. */
    public AuthCheckServiceImpl(
            ScopedRoleAssignmentService roleAssignmentService,
            PersonalAccessTokenService personalAccessTokenService,
            TokenService tokenService
    ) {
        this(roleAssignmentService, personalAccessTokenService, tokenService, NOOP_METRICS);
    }

    @Override
    public Uni<CheckPermissionResponse> checkPermission(CheckPermissionRequest request) {
        String principalType = "USER";
        long start = System.nanoTime();
        try {
            principalType = toDomainString(request.getPrincipalType());
            UUID principalId = UUID.fromString(request.getPrincipalId());
            final String recordedPrincipalType = principalType;

            return roleAssignmentService.resolveEffectivePermissions(
                            principalType,
                            principalId,
                            request.getRequestedPath()
                    )
                    .map(perms -> {
                        boolean allowed = perms.contains(request.getRequiredPermission());
                        recordDecision("check_permission", recordedPrincipalType, allowed, start);
                        return CheckPermissionResponse.newBuilder().setAllowed(allowed).build();
                    })
                    .onFailure().recoverWithItem(() -> {
                        recordDecision("check_permission", recordedPrincipalType, false, start);
                        return CheckPermissionResponse.newBuilder().setAllowed(false).build();
                    });
        } catch (Exception ex) {
            recordDecision("check_permission", principalType, false, start);
            return Uni.createFrom().item(CheckPermissionResponse.newBuilder().setAllowed(false).build());
        }
    }

    @Override
    public Uni<CheckPermissionsResponse> checkPermissions(CheckPermissionsRequest request) {
        String principalType = "USER";
        long start = System.nanoTime();
        try {
            principalType = toDomainString(request.getPrincipalType());
            UUID principalId = UUID.fromString(request.getPrincipalId());
            final String recordedPrincipalType = principalType;
            List<PathPermissionPair> checks = request.getChecksList();
            List<String> distinctPaths = checks.stream()
                    .map(PathPermissionPair::getRequestedPath)
                    .distinct()
                    .toList();

            List<Uni<Set<String>>> lookups = distinctPaths.stream()
                    .map(path -> roleAssignmentService.resolveEffectivePermissions(
                            recordedPrincipalType, principalId, path))
                    .toList();

            return Uni.join().all(lookups).andFailFast()
                    .map(resultsPerPath -> {
                        Map<String, Set<String>> byPath = new HashMap<>();
                        for (int i = 0; i < distinctPaths.size(); i++) {
                            byPath.put(distinctPaths.get(i), resultsPerPath.get(i));
                        }
                        CheckPermissionsResponse.Builder builder = CheckPermissionsResponse.newBuilder();
                        boolean allAllowed = true;
                        for (PathPermissionPair pair : checks) {
                            Set<String> perms = byPath.getOrDefault(pair.getRequestedPath(), Set.of());
                            boolean allowed = perms.contains(pair.getRequiredPermission());
                            allAllowed &= allowed;
                            builder.addResults(allowed);
                        }
                        recordDecision("check_permissions", recordedPrincipalType, allAllowed, start);
                        return builder.build();
                    })
                    .onFailure().recoverWithItem(() -> {
                        recordDecision("check_permissions", recordedPrincipalType, false, start);
                        CheckPermissionsResponse.Builder builder = CheckPermissionsResponse.newBuilder();
                        checks.forEach(c -> builder.addResults(false));
                        return builder.build();
                    });
        } catch (Exception ex) {
            recordDecision("check_permissions", principalType, false, start);
            CheckPermissionsResponse.Builder builder = CheckPermissionsResponse.newBuilder();
            request.getChecksList().forEach(c -> builder.addResults(false));
            return Uni.createFrom().item(builder.build());
        }
    }

    @Override
    public Uni<ResolvePrincipalResponse> resolvePrincipal(ResolvePrincipalRequest request) {
        String credential = request.getCredential();
        if (credential == null || credential.isBlank()) {
            return Uni.createFrom().item(ResolvePrincipalResponse.newBuilder().setValid(false).build());
        }

        if (credential.startsWith("pat_")) {
            return personalAccessTokenService.validateAndTouch(credential)
                    .map(pat -> {
                        metrics.increment(tokenValidation("pat", true));
                        return ResolvePrincipalResponse.newBuilder()
                                .setValid(true)
                                .setPrincipalType(fromDomainString(pat.principalType()))
                                .setPrincipalId(pat.principalId().toString())
                                .setDisplayName(pat.label() != null ? pat.label() : "")
                                .build();
                    })
                    .onFailure().recoverWithItem(() -> {
                        metrics.increment(tokenValidation("pat", false));
                        return ResolvePrincipalResponse.newBuilder().setValid(false).build();
                    });
        }

        return tokenService.verify(credential)
                .map(principal -> {
                    metrics.increment(tokenValidation("jwt", true));
                    return ResolvePrincipalResponse.newBuilder()
                            .setValid(true)
                            .setPrincipalType(fromDomainString(principal.principalType()))
                            .setPrincipalId(principal.principalId().toString())
                            .setDisplayName(principal.displayName() != null ? principal.displayName() : "")
                            .build();
                })
                .onFailure().recoverWithItem(() -> {
                    metrics.increment(tokenValidation("jwt", false));
                    return ResolvePrincipalResponse.newBuilder().setValid(false).build();
                });
    }

    /** Records latency + outcome of one auth decision ({@code identity.auth.decision.*}). */
    private void recordDecision(String rpcMethod, String principalType, boolean allowed, long startNanos) {
        metrics.record(
                MetricName.of("identity.auth.decision.time").with("rpc_method", rpcMethod),
                Duration.ofNanos(System.nanoTime() - startNanos)
        );
        metrics.increment(
                MetricName.of("identity.auth.decision.result")
                        .with("outcome", allowed ? "allow" : "deny")
                        .with("principal_type", normalizePrincipalType(principalType))
        );
    }

    /** {@code identity.token.validation} counter, tagged with bounded values only. */
    private static MetricName tokenValidation(String type, boolean valid) {
        return MetricName.of("identity.token.validation")
                .with("type", type)
                .with("valid", Boolean.toString(valid));
    }

    private static String normalizePrincipalType(String domainType) {
        return "SERVICE_ACCOUNT".equalsIgnoreCase(domainType) ? "service_account" : "user";
    }

    private static String toDomainString(PrincipalType grpcType) {
        return grpcType == PrincipalType.SERVICE_ACCOUNT ? "SERVICE_ACCOUNT" : "USER";
    }

    private static PrincipalType fromDomainString(String domainType) {
        if ("SERVICE_ACCOUNT".equalsIgnoreCase(domainType) || "SERVICE".equalsIgnoreCase(domainType)) {
            return PrincipalType.SERVICE_ACCOUNT;
        }
        return PrincipalType.USER;
    }
}
