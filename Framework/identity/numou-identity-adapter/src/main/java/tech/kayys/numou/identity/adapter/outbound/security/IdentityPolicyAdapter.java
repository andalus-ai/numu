package tech.kayys.syirkah.identity.adapter.outbound.security;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.identity.application.port.EffectivePermissionPort;
import tech.kayys.syirkah.identity.domain.user.UserId;
import tech.kayys.syirkah.security.domain.authorization.Permission;
import tech.kayys.syirkah.security.domain.authorization.Principal;
import tech.kayys.syirkah.security.spi.port.PolicyPort;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements Security SPI {@link PolicyPort} using Identity's effective permissions (security02.md §P3-16).
 */
@ApplicationScoped
public final class IdentityPolicyAdapter implements PolicyPort {

    private final EffectivePermissionPort permissions;

    public IdentityPolicyAdapter(EffectivePermissionPort permissions) {
        this.permissions = Objects.requireNonNull(permissions, "permissions port cannot be null");
    }

    @Override
    public Uni<Set<Permission>> permissionsFor(
            Principal principal,
            TenantId tenantId) {

        if (principal == null || tenantId == null || !principal.isMemberOf(tenantId)) {
            return Uni.createFrom().item(Set.of());
        }

        final UserId userId;
        try {
            userId = UserId.of(principal.id().value());
        } catch (RuntimeException e) {
            return Uni.createFrom().item(Set.of());
        }

        return permissions.permissionsFor(tenantId, userId)
                .map(codes -> codes.stream()
                        .map(IdentityPermissionMapper::toSecurityPermission)
                        .collect(Collectors.toUnmodifiableSet()));
    }
}
