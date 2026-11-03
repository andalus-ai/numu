package tech.kayys.numou.security.vault.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.security.application.authz.AuthorizationService;
import tech.kayys.numou.security.application.context.SecurityContext;
import tech.kayys.numou.security.domain.valueobject.ActionType;
import tech.kayys.numou.security.vault.domain.VaultPolicyRule;
import tech.kayys.numou.security.vault.domain.VaultPolicySnapshot;
import tech.kayys.numou.security.vault.domain.VaultPolicyChangeType;
import tech.kayys.numou.security.vault.spi.VaultPolicyRepositoryPort;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Authorized policy publication use case; persistence owns revision CAS and atomic audit. */
public final class VaultPolicyAdministrationService {
    private static final String RESOURCE = "vault-policy";
    private final AuthorizationService authorization;
    private final VaultPolicyRepositoryPort policies;
    private final Clock clock;

    public VaultPolicyAdministrationService(AuthorizationService authorization,
            VaultPolicyRepositoryPort policies, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization is required");
        this.policies = Objects.requireNonNull(policies, "policies is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    /**
     * Publishes a complete replacement snapshot for the active tenant.
     * The platform authorization grant is checked before the repository is called.
     */
    public Uni<VaultPolicySnapshot> publish(SecurityContext context, long expectedRevision,
                                             List<VaultPolicyRule> rules) {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(rules, "rules are required");
        return authorization.require(context, ActionType.UPDATE, RESOURCE, context.tenantId().toString())
                .flatMap(ignored -> policies.publish(context.tenantId(), expectedRevision,
                        rules, context.actor(), clock.instant()));
    }
    /**
     * Rolls back by publishing a fresh revision copied from a historical snapshot.
     * Published history is never edited or made active by pointer rewrites.
     */
    public Uni<VaultPolicySnapshot> rollback(SecurityContext context, long expectedActiveRevision,
                                              long targetRevision) {
        Objects.requireNonNull(context, "context is required");
        if (expectedActiveRevision < 1 || targetRevision < 1 || targetRevision >= expectedActiveRevision)
            return Uni.createFrom().failure(new IllegalArgumentException(
                    "rollback requires a historical target below the expected active revision"));
        return authorization.require(context, ActionType.PERFORM, "vault-policy-rollback",
                        context.tenantId().toString())
                .flatMap(ignored -> policies.loadActive(context.tenantId()))
                .flatMap(active -> {
                    long actual = active.map(VaultPolicySnapshot::revision).orElse(0L);
                    if (actual != expectedActiveRevision)
                        return Uni.createFrom().failure(new IllegalStateException(
                                "active vault policy revision changed; reload before rollback"));
                    return policies.loadRevision(context.tenantId(), targetRevision)
                            .flatMap(historical -> {
                                VaultPolicySnapshot source = historical.orElseThrow(
                                        () -> new IllegalArgumentException("target policy revision does not exist"));
                                List<VaultPolicyRule> copied = source.rules().stream()
                                        .map(rule -> new VaultPolicyRule(rule.ruleId(), rule.tenantId(),
                                                expectedActiveRevision + 1, rule.subjectType(), rule.subject(),
                                                rule.workloadIssuer(), rule.workloadAudience(), rule.vaultId(),
                                                rule.secretId(), rule.operation(), rule.effect(),
                                                rule.validFrom(), rule.validUntil()))
                                        .toList();
                                return policies.publish(context.tenantId(), expectedActiveRevision, copied,
                                        context.actor(), clock.instant(), VaultPolicyChangeType.ROLLBACK,
                                        targetRevision);
                            });
                });
    }

}
