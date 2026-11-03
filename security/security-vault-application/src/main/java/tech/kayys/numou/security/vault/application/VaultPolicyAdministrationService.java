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
     * Publishes only after distinct verified principals independently authorize the operation.
     * Host composition must obtain the approver context from a separate authenticated action;
     * the repository records stable principal IDs rather than mutable display handles.
     */
    public Uni<VaultPolicySnapshot> publish(SecurityContext proposer, SecurityContext approver,
                                             long expectedRevision, List<VaultPolicyRule> rules) {
        validateDistinctActors(proposer, approver);
        Objects.requireNonNull(rules, "rules are required");
        return authorization.require(proposer, ActionType.UPDATE, RESOURCE, proposer.tenantId().toString())
                .flatMap(ignored -> authorization.require(approver, ActionType.PERFORM,
                        "vault-policy-approve", approver.tenantId().toString()))
                .flatMap(ignored -> policies.publish(proposer.tenantId(), expectedRevision,
                        rules, proposer.principal().id().value().toString(), approver.principal().id().value().toString(), clock.instant(),
                        VaultPolicyChangeType.PUBLISH, null));
    }


    /**
     * Rolls back by publishing a fresh revision copied from a historical snapshot.
     * Published history is never edited or made active by pointer rewrites.
     */
    public Uni<VaultPolicySnapshot> rollback(SecurityContext proposer, SecurityContext approver, long expectedActiveRevision,
                                              long targetRevision) {
        validateDistinctActors(proposer, approver);
        if (expectedActiveRevision < 1 || expectedActiveRevision == Long.MAX_VALUE
                || targetRevision < 1 || targetRevision >= expectedActiveRevision)
            return Uni.createFrom().failure(new IllegalArgumentException(
                    "rollback requires a historical target below the expected active revision"));
        return authorization.require(proposer, ActionType.UPDATE, RESOURCE, proposer.tenantId().toString())
                .flatMap(ignored -> authorization.require(approver, ActionType.PERFORM,
                        "vault-policy-rollback-approve", approver.tenantId().toString()))
                .flatMap(ignored -> policies.loadActive(proposer.tenantId()))
                .flatMap(active -> {
                    long actual = active.map(VaultPolicySnapshot::revision).orElse(0L);
                    if (actual != expectedActiveRevision)
                        return Uni.createFrom().failure(new IllegalStateException(
                                "active vault policy revision changed; reload before rollback"));
                    return policies.loadRevision(proposer.tenantId(), targetRevision)
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
                                return policies.publish(proposer.tenantId(), expectedActiveRevision, copied,
                                        proposer.principal().id().value().toString(), approver.principal().id().value().toString(), clock.instant(),
                                        VaultPolicyChangeType.ROLLBACK, targetRevision);
                            });
                });
    }

    private static void validateDistinctActors(SecurityContext proposer, SecurityContext approver) {
        Objects.requireNonNull(proposer, "proposer context is required");
        Objects.requireNonNull(approver, "approver context is required");
        if (!proposer.tenantId().equals(approver.tenantId()))
            throw new IllegalArgumentException("proposer and approver must act in the same tenant");
        if (proposer.principal().id().equals(approver.principal().id()))
            throw new IllegalArgumentException("policy proposer cannot approve their own change");
    }

}
