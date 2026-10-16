package tech.kayys.syirkah.identity.application.security;

import java.util.Objects;
import java.util.Set;

public record AuthorizationRequirement(
        String resource,
        String action,
        Set<String> anyOfRoles
) {
    public AuthorizationRequirement {
        Objects.requireNonNull(resource, "resource cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        if (!resource.matches("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*")
                || !action.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("resource and action must be lowercase permission segments");
        }
        anyOfRoles = anyOfRoles == null ? Set.of() : Set.copyOf(anyOfRoles);
    }

    public static AuthorizationRequirement permission(String resource, String action) {
        return new AuthorizationRequirement(resource, action, Set.of());
    }

    public String permissionName() {
        return resource + "." + action;
    }
}
