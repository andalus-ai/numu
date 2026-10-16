package tech.kayys.syirkah.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.identity.application.port.AuthorizationPort;
import tech.kayys.syirkah.identity.application.port.TenantMembershipPort;
import tech.kayys.syirkah.identity.application.security.AuthorizationRequirement;
import tech.kayys.syirkah.identity.application.security.Principal;
import tech.kayys.syirkah.identity.application.security.PrincipalType;

import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class PostgresAuthorizationRepository implements TenantMembershipPort, AuthorizationPort {
    private static final String ACTIVE_MEMBERSHIP =
            "select count(m.id) from TenantMembershipEntity m, UserEntity u "
                    + "where m.tenantId = :tenantId and m.userId = :userId and m.status = 'ACTIVE' "
                    + "and u.id = m.userId and u.status = 'ACTIVE'";

    @Override
    public Uni<Boolean> isMember(Principal principal, String tenantId) {
        var tenantUuid = parseTenant(tenantId);
        var actorId = actorId(principal);
        if (tenantUuid == null || actorId == null) {
            return Uni.createFrom().item(false);
        }
        if (principal.type() == PrincipalType.SERVICE) {
            return Panache.getSession()
                    .chain(session -> session.createSelectionQuery(
                                    "select count(s.id) from ServiceAccountEntity s "
                                            + "where s.tenantId = :tenantId and s.id = :actorId "
                                            + "and s.status = 'ACTIVE'",
                                    Long.class
                            )
                            .setParameter("tenantId", tenantUuid)
                            .setParameter("actorId", actorId)
                            .getSingleResult())
                    .map(count -> count > 0);
        }
        return Panache.getSession()
                .chain(session -> session.createSelectionQuery(ACTIVE_MEMBERSHIP, Long.class)
                        .setParameter("tenantId", tenantUuid)
                        .setParameter("userId", actorId)
                        .getSingleResult())
                .map(count -> count > 0);
    }

    @Override
    public Uni<Set<String>> roles(Principal principal, String tenantId) {
        var tenantUuid = parseTenant(tenantId);
        var actorId = actorId(principal);
        if (tenantUuid == null || actorId == null) {
            return Uni.createFrom().item(Set.of());
        }
        if (principal.type() == PrincipalType.SERVICE) {
            var serviceQuery = """
                    select distinct r.code
                    from ServiceAccountRoleEntity ur, TenantRoleEntity r, ServiceAccountEntity s
                    where ur.tenantId = :tenantId and ur.serviceAccountId = :actorId
                      and ur.roleId = r.id and r.tenantId = ur.tenantId
                      and s.tenantId = ur.tenantId and s.id = ur.serviceAccountId and s.status = 'ACTIVE'
                    """;
            return Panache.getSession()
                    .chain(session -> session.createSelectionQuery(serviceQuery, String.class)
                            .setParameter("tenantId", tenantUuid)
                            .setParameter("actorId", actorId)
                            .getResultList())
                    .map(Set::copyOf);
        }
        var query = """
                select distinct r.code
                from TenantUserRoleEntity ur, TenantRoleEntity r, TenantMembershipEntity m, UserEntity u
                where ur.tenantId = :tenantId and ur.userId = :userId
                  and ur.roleId = r.id and r.tenantId = ur.tenantId
                  and m.tenantId = ur.tenantId and m.userId = ur.userId and m.status = 'ACTIVE'
                  and u.id = ur.userId and u.status = 'ACTIVE'
                """;
        return Panache.getSession()
                .chain(session -> session.createSelectionQuery(query, String.class)
                        .setParameter("tenantId", tenantUuid)
                        .setParameter("userId", actorId)
                        .getResultList())
                .map(Set::copyOf);
    }

    @Override
    public Uni<Boolean> isAllowed(
            Principal principal,
            String tenantId,
            AuthorizationRequirement requirement
    ) {
        var tenantUuid = parseTenant(tenantId);
        var actorId = actorId(principal);
        if (tenantUuid == null || actorId == null
                || requirement == null || requirement.permissionName().isBlank()) {
            return Uni.createFrom().item(false);
        }

        var serviceActor = principal.type() == PrincipalType.SERVICE;
        var actorRole = serviceActor ? "ServiceAccountRoleEntity" : "TenantUserRoleEntity";
        var actorJoin = serviceActor
                ? "s.tenantId = ur.tenantId and s.id = ur.serviceAccountId and s.status = 'ACTIVE'"
                : "m.tenantId = ur.tenantId and m.userId = ur.userId and m.status = 'ACTIVE' "
                        + "and u.id = ur.userId and u.status = 'ACTIVE'";
        var actorColumns = serviceActor
                ? "ur.serviceAccountId = :actorId"
                : "ur.userId = :actorId";
        var actorEntities = serviceActor
                ? "ServiceAccountEntity s"
                : "TenantMembershipEntity m, UserEntity u";
        var roleClause = requirement.anyOfRoles().isEmpty() ? "" : " and r.code in :roleCodes ";
        var query = "select count(distinct ur.id) from " + actorRole
                + " ur, TenantRoleEntity r, TenantRolePermissionEntity rp, IdentityPermissionEntity p, "
                + actorEntities + " where ur.tenantId = :tenantId and " + actorColumns
                + " and ur.roleId = r.id and r.tenantId = ur.tenantId"
                + " and rp.tenantId = ur.tenantId and rp.roleId = ur.roleId"
                + " and rp.permissionId = p.id and p.code = :permission and " + actorJoin + roleClause;

        var selection = Panache.getSession()
                .chain(session -> {
                    var queryBuilder = session.createSelectionQuery(query, Long.class)
                            .setParameter("tenantId", tenantUuid)
                            .setParameter("actorId", actorId)
                            .setParameter("permission", requirement.permissionName());
                    if (!requirement.anyOfRoles().isEmpty()) {
                        queryBuilder.setParameter("roleCodes", requirement.anyOfRoles());
                    }
                    return queryBuilder.getSingleResult();
                });
        return selection.map(count -> count > 0);
    }

    private static UUID actorId(Principal principal) {
        if (principal == null || !principal.authenticated()) {
            return null;
        }
        if (principal.type() == PrincipalType.USER) {
            return principal.userId() == null ? null : principal.userId().value();
        }
        try {
            return UUID.fromString(principal.subject());
        } catch (IllegalArgumentException invalidServiceIdentity) {
            return null;
        }
    }

    private static UUID parseTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(tenantId);
        } catch (IllegalArgumentException invalidTenantId) {
            return null;
        }
    }
}
