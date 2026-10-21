package tech.kayys.numou.inventory.domain.inventory;

import java.util.Objects;

/** Stable idempotency scope for a caller-issued inventory command. */
public record InventoryCommandKey(String scope, String key) {
    public InventoryCommandKey {
        Objects.requireNonNull(scope, "scope is required");
        Objects.requireNonNull(key, "key is required");
        scope = scope.trim(); key = key.trim();
        if (scope.isEmpty() || key.isEmpty()) throw new IllegalArgumentException("Scope and key must not be blank");
        if (scope.length() > 200 || key.length() > 200) throw new IllegalArgumentException("Scope and key must not exceed 200 characters");
    }
}
