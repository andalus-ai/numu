package tech.kayys.syirkah.identity.domain.role;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Role extends AbstractAggregateRoot<RoleId> {
    private final TenantId tenantId;
    private final String code;
    private String name;
    private final boolean builtIn;
    private final Set<Permission> permissions = new LinkedHashSet<>();

    private Role(RoleId id, TenantId tenantId, String code, String name, boolean builtIn) {
        super(Objects.requireNonNull(id, "id cannot be null"));
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.code = requireText(code, "code").toLowerCase(java.util.Locale.ROOT);
        this.name = requireText(name, "name");
        this.builtIn = builtIn;
    }

    public static Role create(RoleId id, TenantId tenantId, String code, String name, Instant occurredAt) {
        var role = new Role(id, tenantId, code, name, false);
        role.raise(new RoleCreated(
                UUID.randomUUID(), Objects.requireNonNull(occurredAt), id, tenantId, role.code, role.name, false
        ));
        return role;
    }

    public static Role defaultRole(RoleId id, TenantId tenantId, String code, String name, Instant occurredAt) {
        var role = new Role(id, tenantId, code, name, true);
        role.raise(new RoleCreated(
                UUID.randomUUID(), Objects.requireNonNull(occurredAt), id, tenantId, role.code, role.name, true
        ));
        return role;
    }

    public static Role reconstitute(
            RoleId id,
            TenantId tenantId,
            String code,
            String name,
            boolean builtIn,
            Set<Permission> permissions
    ) {
        var role = new Role(id, tenantId, code, name, builtIn);
        role.permissions.addAll(Objects.requireNonNull(permissions, "permissions cannot be null"));
        return role;
    }

    public void rename(String name) {
        if (builtIn) {
            throw new IllegalStateException("Built-in roles cannot be renamed");
        }
        this.name = requireText(name, "name");
    }

    public void grant(Permission permission, Instant now) {
        if (permissions.add(Objects.requireNonNull(permission))) {
            raise(new RolePermissionChanged(UUID.randomUUID(), Objects.requireNonNull(now), id(), tenantId,
                    permission, true));
        }
    }

    public void revoke(Permission permission, Instant now) {
        if (builtIn) {
            throw new IllegalStateException("Permissions cannot be removed from built-in roles");
        }
        if (permissions.remove(Objects.requireNonNull(permission))) {
            raise(new RolePermissionChanged(UUID.randomUUID(), Objects.requireNonNull(now), id(), tenantId,
                    permission, false));
        }
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    public TenantId tenantId() { return tenantId; }
    public String code() { return code; }
    public String name() { return name; }
    public boolean builtIn() { return builtIn; }
    public Set<Permission> permissions() { return Set.copyOf(permissions); }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }
}
