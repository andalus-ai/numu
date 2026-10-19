package tech.kayys.numou.inventory.domain.location;

import tech.kayys.numou.inventory.domain.inventory.WarehouseId;

import java.util.Objects;

/**
 * Hierarchical storage location inside a warehouse (aisle, rack, bin, staging area).
 */
public record StorageLocation(
        StorageLocationId id,
        WarehouseId warehouseId,
        StorageLocationId parentId,
        String code,
        String name,
        StorageLocationType type,
        StorageLocationStatus status
) {
    public StorageLocation {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(warehouseId, "warehouseId cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(status, "status cannot be null");

        code = code.trim().toUpperCase();
        name = name.trim();

        if (code.isEmpty() || name.isEmpty()) {
            throw new IllegalArgumentException("Storage location code and name must not be blank");
        }

        if (id.equals(parentId)) {
            throw new IllegalArgumentException("A storage location cannot be its own parent");
        }
    }
}
