package tech.kayys.syirkah.inventory.domain.location;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a storage location within a warehouse (bin, rack, aisle, zone).
 */
public record StorageLocationId(String value) {
    public StorageLocationId {
        Objects.requireNonNull(value, "value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("StorageLocationId cannot be blank");
        }
    }

    public static StorageLocationId generate() {
        return new StorageLocationId(UUID.randomUUID().toString());
    }

    public static StorageLocationId of(String value) {
        return new StorageLocationId(value);
    }
}
