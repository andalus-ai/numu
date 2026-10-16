package tech.kayys.syirkah.identity.adapter.outbound.security;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.identity.application.port.MembershipManagementPort;
import tech.kayys.syirkah.identity.domain.user.UserId;
import tech.kayys.syirkah.security.domain.authorization.Principal;
import tech.kayys.syirkah.security.spi.port.TenantMembershipResolver;

import java.util.Objects;

/**
 * Implements Security SPI {@link TenantMembershipResolver} using Identity's membership records (security02.md §P3-18).
 */
@ApplicationScoped
public final class IdentityTenantMembershipAdapter implements TenantMembershipResolver {

    private final MembershipManagementPort membership;

    public IdentityTenantMembershipAdapter(MembershipManagementPort membership) {
        this.membership = Objects.requireNonNull(membership, "membership cannot be null");
    }

    @Override
    public Uni<Boolean> isMember(Principal principal, TenantId tenantId) {
        if (principal == null || tenantId == null) {
            return Uni.createFrom().item(false);
        }

        final UserId userId;
        try {
            userId = UserId.of(principal.id().value());
        } catch (RuntimeException e) {
            return Uni.createFrom().item(false);
        }

        return membership.isActive(tenantId, userId);
    }
}
