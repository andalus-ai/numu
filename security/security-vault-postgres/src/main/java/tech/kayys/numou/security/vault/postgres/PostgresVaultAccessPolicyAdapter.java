package tech.kayys.numou.security.vault.postgres;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.security.domain.authorization.Principal;
import tech.kayys.numou.security.vault.domain.*;
import tech.kayys.numou.security.vault.spi.*;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Production policy decision adapter. Principal identity mapping remains host-owned;
 * this adapter evaluates one persisted snapshot and waits for durable audit before
 * allowing access. Any load or audit failure propagates and therefore fails closed.
 */
public final class PostgresVaultAccessPolicyAdapter implements VaultAccessPolicyPort {
    private final VaultPolicyRepositoryPort policies;
    private final VaultPolicyDecisionAuditPort audit;
    private final VaultPolicyEvaluator evaluator;
    private final Function<Principal, UUID> principalId;
    private final Clock clock;

    public PostgresVaultAccessPolicyAdapter(VaultPolicyRepositoryPort policies,
            VaultPolicyDecisionAuditPort audit, VaultPolicyEvaluator evaluator,
            Function<Principal, UUID> principalId, Clock clock) {
        this.policies = Objects.requireNonNull(policies, "policies is required");
        this.audit = Objects.requireNonNull(audit, "audit is required");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator is required");
        this.principalId = Objects.requireNonNull(principalId, "principalId mapper is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public Uni<Void> authorize(VaultAccessRequest request) {
        Objects.requireNonNull(request, "request is required");
        UUID resolvedPrincipal = Objects.requireNonNull(principalId.apply(request.principal()),
                "principal mapper returned null");
        return policies.loadActive(request.tenantId()).flatMap(snapshot -> {
            long revision = snapshot.map(VaultPolicySnapshot::revision).orElse(0L);
            var rules = snapshot.map(VaultPolicySnapshot::rules).orElseGet(java.util.List::of);
            Optional<VaultWorkloadIdentity> workload = request.workloadIdentity();
            var decision = evaluator.evaluate(request.tenantId(), resolvedPrincipal, request.roles(),
                    workload.orElse(null), request.vaultId(), request.secretId(), request.operation(),
                    rules, revision, clock.instant());
            String subjectReference = workload
                    .map(PostgresVaultAccessPolicyAdapter::workloadAuditReference)
                    .orElseGet(() -> "principal:" + resolvedPrincipal);
            var auditRecord = new VaultPolicyDecisionAudit(UUID.randomUUID(), subjectReference,
                    request.vaultId(), request.secretId(), decision, null);
            return audit.append(auditRecord).flatMap(ignored -> {
                if (decision.effect() == VaultPolicyEffect.ALLOW) return Uni.createFrom().voidItem();
                return Uni.createFrom().failure(new VaultPolicyAccessDeniedException());
            });
        });
    }

    private static String workloadAuditReference(VaultWorkloadIdentity identity) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest((identity.issuer().length() + ":" + identity.issuer() + identity.subject())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "workload-sha256:" + java.util.HexFormat.of().formatHex(value);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
