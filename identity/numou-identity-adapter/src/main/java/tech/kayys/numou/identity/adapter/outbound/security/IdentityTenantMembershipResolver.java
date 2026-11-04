package tech.kayys.numou.identity.adapter.outbound.security;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.application.port.MembershipManagementPort;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.security.domain.authorization.Principal;
import tech.kayys.numou.security.spi.port.TenantMembershipResolver;

import java.util.Objects;

/**
 * Bridges the public Security membership contract to Identity's authoritative
 * tenant-membership application port.
 *
 * <p>Identity remains the owner of membership persistence and lifecycle;
 * Security consumes only the narrow membership decision.
 */
@ApplicationScoped
public class IdentityTenantMembershipResolver implements TenantMembershipResolver {

    private final MembershipManagementPort memberships;

    @Inject
    public IdentityTenantMembershipResolver(MembershipManagementPort memberships) {
        this.memberships = Objects.requireNonNull(memberships, "memberships");
    }

    @Override
    public Uni<Boolean> isMember(Principal principal, TenantId tenantId) {
        if (principal == null || principal.id() == null || tenantId == null) {
            return Uni.createFrom().item(false);
        }
        return memberships.isActive(tenantId, UserId.of(principal.id().value()));
    }
}
