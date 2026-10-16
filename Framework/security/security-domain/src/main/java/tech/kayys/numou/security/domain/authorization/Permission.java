package tech.kayys.syirkah.security.domain.authorization;

import tech.kayys.syirkah.security.domain.valueobject.ActionType;

import java.util.Objects;

/**
 * Canonical permission representation: {@code resource.action} (e.g. {@code "product.read"}).
 *
 * @param resource the domain concept or bounded context entity (e.g. "product")
 * @param action   the action/operation performed on the resource (e.g. "read", "create")
 */
public record Permission(String resource, String action) {

    public Permission {
        Objects.requireNonNull(resource, "resource cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        if (resource.isBlank()) {
            throw new IllegalArgumentException("resource cannot be blank");
        }
        if (action.isBlank()) {
            throw new IllegalArgumentException("action cannot be blank");
        }
    }

    /**
     * Canonical factory: normalizes resource and action to lowercase.
     */
    public static Permission of(String resource, String action) {
        Objects.requireNonNull(resource, "resource cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        return new Permission(resource.trim().toLowerCase(), action.trim().toLowerCase());
    }

    /**
     * Convenience factory for enum-based actions.
     */
    public static Permission of(ActionType action, String resourceType) {
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(resourceType, "resourceType cannot be null");
        return of(resourceType, action.name());
    }

    /**
     * Alias for {@link #resource()} for backward compatibility.
     */
    public String resourceType() {
        return resource;
    }

    /**
     * Canonical string representation {@code resource.action}.
     */
    public String name() {
        return resource + "." + action;
    }
}
