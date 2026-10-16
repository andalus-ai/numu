package tech.kayys.syirkah.identity.adapter.outbound.security;

import tech.kayys.syirkah.security.domain.authorization.Permission;

/**
 * Maps Identity domain permission representations into canonical Security domain Permissions (security02.md §P3-16).
 */
public final class IdentityPermissionMapper {

    private IdentityPermissionMapper() {
    }

    /**
     * Translates a qualified string permission (e.g. "order.read", "sales.order.read")
     * into a canonical Security Permission using the last dot as the delimiter.
     */
    public static Permission toSecurityPermission(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Permission code cannot be null or blank");
        }
        var separator = value.lastIndexOf('.');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Invalid identity permission code format: " + value);
        }
        return Permission.of(
                value.substring(0, separator),
                value.substring(separator + 1)
        );
    }

    /**
     * Translates an Identity Permission domain object into canonical Security Permission.
     */
    public static Permission toSecurityPermission(tech.kayys.syirkah.identity.domain.role.Permission permission) {
        if (permission == null) {
            throw new IllegalArgumentException("permission cannot be null");
        }
        return toSecurityPermission(permission.value());
    }
}
