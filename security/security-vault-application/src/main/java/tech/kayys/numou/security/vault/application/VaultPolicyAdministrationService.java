package tech.kayys.numou.security.vault.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.security.application.authz.AuthorizationService;
import tech.kayys.numou.security.application.context.SecurityContext;
import tech.kayys.numou.security.domain.valueobject.ActionType;
import tech.kayys.numou.security.vault.domain.VaultPolicyRule;
import tech.kayys.numou.security.vault.domain.VaultPolicySnapshot;
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
}
