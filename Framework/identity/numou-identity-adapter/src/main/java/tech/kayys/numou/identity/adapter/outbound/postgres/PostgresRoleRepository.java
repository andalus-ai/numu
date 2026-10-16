package tech.kayys.syirkah.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.identity.application.port.RoleRepository;
import tech.kayys.syirkah.identity.domain.role.Permission;
import tech.kayys.syirkah.identity.domain.role.Role;
import tech.kayys.syirkah.identity.domain.role.RoleId;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class PostgresRoleRepository implements RoleRepository {
    @Override
    public Uni<Optional<Role>> findById(TenantId tenantId, RoleId roleId) {
        return TenantRoleEntity.<TenantRoleEntity>find(
                        "tenantId = ?1 and id = ?2",
                        tenantId.value(),
                        roleId.value()
                )
                .firstResult()
                .chain(entity -> entity == null
                        ? Uni.createFrom().item(Optional.empty())
                        : toDomain(entity).map(Optional::of));
    }

    @Override
    public Uni<Optional<Role>> findByCode(TenantId tenantId, String code) {
        return TenantRoleEntity.<TenantRoleEntity>find(
                        "tenantId = ?1 and code = ?2",
                        tenantId.value(),
                        code
                )
                .firstResult()
                .chain(entity -> entity == null
                        ? Uni.createFrom().item(Optional.empty())
                        : toDomain(entity).map(Optional::of));
    }

    @Override
    public Uni<Void> save(Role role) {
        var tenantId = role.tenantId().value();
        var roleId = role.id().value();
        return TenantRoleEntity.<TenantRoleEntity>find("tenantId = ?1 and id = ?2", tenantId, roleId)
                .firstResult()
                .chain(existing -> {
                    var isNew = existing == null;
                    var entity = isNew ? new TenantRoleEntity() : existing;
                    entity.id = roleId;
                    entity.tenantId = tenantId;
                    entity.code = role.code();
                    entity.name = role.name();
                    entity.builtIn = role.builtIn();
                    var persist = isNew
                            ? Panache.getSession().chain(session -> session.persist(entity))
                            : Uni.createFrom().voidItem();
                    return persist.chain(() -> synchronizePermissions(role, tenantId, roleId));
                });
    }

    private Uni<Role> toDomain(TenantRoleEntity entity) {
        return TenantRolePermissionEntity.<TenantRolePermissionEntity>find(
                        "tenantId = ?1 and roleId = ?2",
                        entity.tenantId,
                        entity.id
                )
                .list()
                .chain(links -> {
                    if (links.isEmpty()) {
                        return Uni.createFrom().item(Set.<Permission>of());
                    }
                    var permissionIds = links.stream().map(link -> link.permissionId).toList();
                    return IdentityPermissionEntity.<IdentityPermissionEntity>find("id in ?1", permissionIds)
                            .list()
                            .map(entities -> entities.stream().map(row -> Permission.of(row.code))
                                    .collect(java.util.stream.Collectors.toUnmodifiableSet()));
                })
                .map(permissions -> Role.reconstitute(
                        new RoleId(entity.id),
                        TenantId.of(entity.tenantId),
                        entity.code,
                        entity.name,
                        entity.builtIn,
                        permissions
                ));
    }

    private Uni<Void> synchronizePermissions(Role role, UUID tenantId, UUID roleId) {
        var codes = role.permissions().stream().map(Permission::value).collect(java.util.stream.Collectors.toSet());
        Uni<java.util.List<IdentityPermissionEntity>> desiredPermissions;
        if (codes.isEmpty()) {
            desiredPermissions = Uni.createFrom().item(java.util.List.of());
        } else {
            desiredPermissions = IdentityPermissionEntity.<IdentityPermissionEntity>find("code in ?1", codes).list();
        }
        return desiredPermissions.chain(registered -> {
            var registeredCodes = registered.stream().map(permission -> permission.code)
                    .collect(java.util.stream.Collectors.toSet());
            if (!registeredCodes.equals(codes)) {
                var missing = new HashSet<>(codes);
                missing.removeAll(registeredCodes);
                return Uni.createFrom().failure(new IllegalArgumentException(
                        "Permissions must be registered before role grant: " + String.join(", ", missing)
                ));
            }
            return TenantRolePermissionEntity.<TenantRolePermissionEntity>find(
                            "tenantId = ?1 and roleId = ?2", tenantId, roleId
                    )
                    .list()
                    .chain(existing -> {
                        var desiredIds = registered.stream().map(permission -> permission.id)
                                .collect(java.util.stream.Collectors.toSet());
                        var existingIds = existing.stream().map(link -> link.permissionId)
                                .collect(java.util.stream.Collectors.toSet());
                        Uni<Void> changes = Uni.createFrom().voidItem();
                        for (var link : existing) {
                            if (!desiredIds.contains(link.permissionId)) {
                                changes = changes.chain(() -> TenantRolePermissionEntity.deleteById(link.id)
                                        .replaceWithVoid());
                            }
                        }
                        for (var permission : registered) {
                            if (!existingIds.contains(permission.id)) {
                                var link = new TenantRolePermissionEntity();
                                link.id = UUID.randomUUID();
                                link.tenantId = tenantId;
                                link.roleId = roleId;
                                link.permissionId = permission.id;
                                changes = changes.chain(() ->
                                        Panache.getSession().chain(session -> session.persist(link))
                                );
                            }
                        }
                        return changes;
                    });
        });
    }
}
