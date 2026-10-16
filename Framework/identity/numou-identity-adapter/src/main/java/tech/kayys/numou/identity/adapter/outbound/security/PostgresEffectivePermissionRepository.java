package tech.kayys.syirkah.identity.adapter.outbound.security;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.identity.application.port.EffectivePermissionPort;
import tech.kayys.syirkah.identity.domain.user.UserId;

import java.util.Set;

/**
 * Single-query read-side repository for resolving effective permissions (security02.md §P3-16).
 */
@ApplicationScoped
public class PostgresEffectivePermissionRepository implements EffectivePermissionPort {

    @Override
    public Uni<Set<String>> permissionsFor(
            TenantId tenantId,
            UserId userId) {

        return Panache.getSession()
                .chain(session ->
                        session.createSelectionQuery(
                                """
                                select distinct p.code
                                from TenantUserRoleEntity ur,
                                     TenantRoleEntity r,
                                     TenantRolePermissionEntity rp,
                                     IdentityPermissionEntity p,
                                     TenantMembershipEntity m,
                                     UserEntity u
                                where ur.tenantId = :tenantId
                                  and ur.userId = :userId

                                  and r.id = ur.roleId
                                  and r.tenantId = ur.tenantId

                                  and rp.tenantId = ur.tenantId
                                  and rp.roleId = ur.roleId

                                  and p.id = rp.permissionId

                                  and m.tenantId = ur.tenantId
                                  and m.userId = ur.userId
                                  and m.status = 'ACTIVE'

                                  and u.id = ur.userId
                                  and u.status = 'ACTIVE'
                                """,
                                String.class)
                        .setParameter("tenantId", tenantId.value())
                        .setParameter("userId", userId.value())
                        .getResultList())
                .map(Set::copyOf);
    }
}
