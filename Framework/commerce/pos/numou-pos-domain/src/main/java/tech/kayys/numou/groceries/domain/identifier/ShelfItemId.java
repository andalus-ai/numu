package tech.kayys.syirkah.groceries.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Shelf item identifier for tracking shelf placement.
 */
public record ShelfItemId(UUID value) implements DomainId<UUID>, Serializable {

    public ShelfItemId {
        Objects.requireNonNull(value, "ShelfItemId value cannot be null");
    }

    public static ShelfItemId of(UUID value) {
        return new ShelfItemId(value);
    }

    public static ShelfItemId generate() {
        return new ShelfItemId(UUID.randomUUID());
    }

    public static ShelfItemId fromString(String value) {
        return new ShelfItemId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "ShelfItemId{" + value + "}";
    }
}
