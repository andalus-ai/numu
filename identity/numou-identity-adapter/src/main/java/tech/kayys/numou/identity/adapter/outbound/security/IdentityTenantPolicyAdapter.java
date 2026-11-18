package tech.kayys.numou.identity.adapter.outbound.security;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.adapter.outbound.postgres.IdentityPermissionEntity;
import tech.kayys.numou.identity.adapter.outbound.postgres.TenantRolePermissionEntity;
import tech.kayys.numou.identity.adapter.outbound.postgres.TenantUserRoleEntity;
import tech.kayys.numou.security.domain.authorization.Permission;
import tech.kayys.numou.security.domain.authorization.Principal;
import tech.kayys.numou.security.spi.port.PolicyPort;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves a user's permissions exclusively through role assignments owned by
 * the requested tenant. Membership validation remains the responsibility of
 * TenantMembershipResolver and is performed before authorization.
 */
@ApplicationScoped
public class IdentityTenantPolicyAdapter implements PolicyPort {

    @Override
    public Uni<Set<Permission>> permissionsFor(Principal principal, TenantId tenantId) {
        if (principal == null || tenantId == null || principal.id() == null) {
            return Uni.createFrom().item(Set.of());
        }

        return Panache.getSession()
                .chain(session -> session.createSelectionQuery(
                                "select distinct permission.code "
                                        + "from TenantUserRoleEntity userRole, "
                                        + "TenantRolePermissionEntity rolePermission, "
                                        + "IdentityPermissionEntity permission "
                                        + "where userRole.tenantId = :tenantId "
                                        + "and userRole.userId = :userId "
                                        + "and rolePermission.tenantId = userRole.tenantId "
                                        + "and rolePermission.roleId = userRole.roleId "
                                        + "and permission.id = rolePermission.permissionId",
                                String.class)
                        .setParameter("tenantId", tenantId.value())
                        .setParameter("userId", principal.id().value())
                        .getResultList())
                .map(codes -> codes.stream()
                        .map(IdentityPermissionMapper::toSecurityPermission)
                        .collect(Collectors.toUnmodifiableSet()));
    }
}
