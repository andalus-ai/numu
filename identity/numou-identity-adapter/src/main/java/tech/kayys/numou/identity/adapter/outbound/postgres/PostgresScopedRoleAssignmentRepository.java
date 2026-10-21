package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.identity.application.port.ScopedRoleAssignmentRepository;
import tech.kayys.numou.identity.domain.role.RoleId;
import tech.kayys.numou.identity.domain.role.assignment.RoleAssignment;
import tech.kayys.numou.identity.domain.role.assignment.RoleAssignmentId;
import tech.kayys.numou.identity.domain.role.assignment.RoleScopeType;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class PostgresScopedRoleAssignmentRepository implements ScopedRoleAssignmentRepository {

    @Override
    public Uni<List<RoleId>> findApplicableRoleIds(String principalType, UUID principalId, String requestedPath) {
        return Panache.getSession().chain(session -> session
                .createQuery(
                        """
                        select distinct ra.roleId
                        from RoleAssignmentEntity ra
                        where ra.principalType = ?1
                          and ra.principalId = ?2
                          and (ra.expiresAt is null or ra.expiresAt > ?3)
                          and (ra.scopePath = ?4 or ?4 like concat(ra.scopePath, '.%'))
                        """,
                        UUID.class)
                .setParameter(1, principalType)
                .setParameter(2, principalId)
                .setParameter(3, Instant.now())
                .setParameter(4, requestedPath)
                .getResultList()
                .map(uuids -> uuids.stream().map(RoleId::new).toList()));
    }

    @Override
    public Uni<List<RoleAssignment>> findByPrincipal(String principalType, UUID principalId) {
        return RoleAssignmentEntity.<RoleAssignmentEntity>find(
                        "principalType = ?1 and principalId = ?2", principalType, principalId)
                .list()
                .map(entities -> entities.stream().map(this::toDomain).toList());
    }

    @Override
    public Uni<List<RoleAssignment>> findHolders(String principalType, RoleId roleId, String scopePath) {
        return RoleAssignmentEntity.<RoleAssignmentEntity>find(
                        "principalType = ?1 and roleId = ?2 and scopePath = ?3",
                        principalType, roleId.value(), scopePath)
                .list()
                .map(entities -> entities.stream().map(this::toDomain).toList());
    }

    @Override
    public Uni<Void> save(RoleAssignment assignment) {
        return RoleAssignmentEntity.<RoleAssignmentEntity>findById(assignment.id().value())
                .chain(existing -> {
                    RoleAssignmentEntity entity = existing != null ? existing : new RoleAssignmentEntity();
                    entity.id = assignment.id().value();
                    entity.principalType = assignment.principalType();
                    entity.principalId = assignment.principalId();
                    entity.roleId = assignment.roleId().value();
                    entity.scopeType = assignment.scopeType().name();
                    entity.scopePath = assignment.scopePath();
                    entity.grantedByUserId = assignment.grantedByUserId() != null ? assignment.grantedByUserId().value() : null;
                    entity.grantedAt = assignment.grantedAt();
                    entity.expiresAt = assignment.expiresAt();

                    return Panache.getSession().chain(session -> session.persist(entity));
                });
    }

    @Override
    public Uni<Long> deleteByPrincipalAndRole(String principalType, UUID principalId, RoleId roleId, String scopePath) {
        return RoleAssignmentEntity.delete(
                "principalType = ?1 and principalId = ?2 and roleId = ?3 and scopePath = ?4",
                principalType, principalId, roleId.value(), scopePath
        );
    }

    @Override
    public Uni<Set<String>> findPermissionCodesForRoles(List<RoleId> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Uni.createFrom().item(Collections.emptySet());
        }

        List<UUID> uuids = roleIds.stream().map(RoleId::value).toList();
        return Panache.getSession().chain(session -> session
                .createQuery(
                        """
                        select distinct p.code
                        from TenantRolePermissionEntity rp,
                             IdentityPermissionEntity p
                        where rp.roleId in ?1
                          and p.id = rp.permissionId
                        """,
                        String.class)
                .setParameter(1, uuids)
                .getResultList()
                .map(Set::copyOf));
    }

    private RoleAssignment toDomain(RoleAssignmentEntity entity) {
        return RoleAssignment.reconstitute(
                RoleAssignmentId.of(entity.id),
                entity.principalType,
                entity.principalId,
                new RoleId(entity.roleId),
                RoleScopeType.valueOf(entity.scopeType.toUpperCase(java.util.Locale.ROOT)),
                entity.scopePath,
                entity.grantedByUserId != null ? UserId.of(entity.grantedByUserId) : null,
                entity.grantedAt,
                entity.expiresAt
        );
    }
}
