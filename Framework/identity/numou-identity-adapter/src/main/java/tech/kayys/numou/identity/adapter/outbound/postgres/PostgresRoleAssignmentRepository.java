package tech.kayys.syirkah.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.identity.application.port.IdentityOutboxPort;
import tech.kayys.syirkah.identity.application.port.RoleAssignmentPort;
import tech.kayys.syirkah.identity.domain.role.RoleId;
import tech.kayys.syirkah.identity.domain.role.TenantRoleAssignmentChanged;
import tech.kayys.syirkah.identity.domain.user.UserId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PostgresRoleAssignmentRepository implements RoleAssignmentPort {
    private final IdentityOutboxPort outbox;

    @Inject
    public PostgresRoleAssignmentRepository(IdentityOutboxPort outbox) {
        this.outbox = outbox;
    }

    @Override
    public Uni<Boolean> isAssigned(TenantId tenantId, UserId userId, RoleId roleId) {
        return TenantUserRoleEntity.<TenantUserRoleEntity>count(
                        "tenantId = ?1 and userId = ?2 and roleId = ?3",
                        tenantId.value(),
                        userId.value(),
                        roleId.value()
                )
                .map(count -> count > 0);
    }

    @Override
    public Uni<java.util.Set<RoleId>> rolesFor(TenantId tenantId, UserId userId) {
        return Panache.getSession()
                .chain(session -> session.createSelectionQuery("""
                        select ur.roleId
                        from TenantUserRoleEntity ur,
                             TenantMembershipEntity m,
                             UserEntity u
                        where ur.tenantId = :tenantId
                          and ur.userId = :userId
                          and m.tenantId = ur.tenantId
                          and m.userId = ur.userId
                          and m.status = 'ACTIVE'
                          and u.id = ur.userId
                          and u.status = 'ACTIVE'
                        """, UUID.class)
                        .setParameter("tenantId", tenantId.value())
                        .setParameter("userId", userId.value())
                        .getResultList())
                .map(roleIds -> roleIds.stream()
                        .map(RoleId::new)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    @Override
    public Uni<Void> assign(
            TenantId tenantId,
            UserId userId,
            RoleId roleId,
            UserId assignedBy,
            Instant assignedAt
    ) {
        var tenantUuid = tenantId.value();
        var assignmentId = UUID.randomUUID();
        return Panache.getSession()
                .chain(session -> session.createNativeQuery("""
                                INSERT INTO tenant_user_roles
                                    (id, tenant_id, user_id, role_id, assigned_at, assigned_by, version)
                                SELECT :id, :tenantId, :userId, :roleId, :assignedAt, :assignedBy, 0
                                  FROM tenant_memberships m
                                  JOIN identity_users u ON u.id = m.user_id
                                  JOIN tenant_roles r ON r.tenant_id = m.tenant_id AND r.id = :roleId
                                 WHERE m.tenant_id = :tenantId
                                   AND m.user_id = :userId
                                   AND m.status = 'ACTIVE'
                                   AND u.status = 'ACTIVE'
                                ON CONFLICT (tenant_id, user_id, role_id) DO NOTHING
                                """)
                        .setParameter("id", assignmentId)
                        .setParameter("tenantId", tenantUuid)
                        .setParameter("userId", userId.value())
                        .setParameter("roleId", roleId.value())
                        .setParameter("assignedAt", assignedAt)
                        .setParameter("assignedBy", assignedBy.value())
                        .executeUpdate())
                .chain(inserted -> {
                    if (inserted == 0) {
                        return isAssigned(tenantId, userId, roleId).chain(alreadyAssigned -> alreadyAssigned
                                ? Uni.createFrom().voidItem()
                                : Uni.createFrom().failure(
                                        new IllegalStateException("Active user membership and tenant role are required")
                                ));
                    }
                    return recordChange(tenantId, userId, roleId, assignedBy, assignedAt, true);
                });
    }

    @Override
    public Uni<Void> remove(
            TenantId tenantId,
            UserId userId,
            RoleId roleId,
            UserId removedBy,
            Instant removedAt
    ) {
        return Panache.getSession()
                .chain(session -> session.createNativeQuery("""
                                DELETE FROM tenant_user_roles
                                 WHERE tenant_id = :tenantId AND user_id = :userId AND role_id = :roleId
                                """)
                        .setParameter("tenantId", tenantId.value())
                        .setParameter("userId", userId.value())
                        .setParameter("roleId", roleId.value())
                        .executeUpdate())
                .chain(removed -> removed == 0
                        ? Uni.createFrom().voidItem()
                        : recordChange(tenantId, userId, roleId, removedBy, removedAt, false));
    }

    private Uni<Void> recordChange(
            TenantId tenantId,
            UserId userId,
            RoleId roleId,
            UserId actorId,
            Instant occurredAt,
            boolean assigned
    ) {
        var event = new TenantRoleAssignmentChanged(
                UUID.randomUUID(), occurredAt, tenantId, userId, roleId, actorId, assigned
        );
        return outbox.append(tenantId, List.of(event))
                .chain(() -> Panache.getSession()
                        .chain(session -> session.createNativeQuery("""
                                        INSERT INTO identity_security_audit
                                            (id, tenant_id, actor_id, actor_type, action, resource_type,
                                             resource_id, decision, reason, occurred_at, attributes)
                                        VALUES
                                            (:id, :tenantId, :actorId, 'USER', :action, 'TENANT_ROLE',
                                             :resourceId, 'ALLOW', NULL, :occurredAt, CAST('{}' AS jsonb))
                                        """)
                                .setParameter("id", UUID.randomUUID())
                                .setParameter("tenantId", tenantId.value())
                                .setParameter("actorId", actorId.value().toString())
                                .setParameter("action", assigned ? "identity.role.assign" : "identity.role.remove")
                                .setParameter("resourceId", roleId.value().toString())
                                .setParameter("occurredAt", occurredAt)
                                .executeUpdate())
                        .replaceWithVoid());
    }
}
