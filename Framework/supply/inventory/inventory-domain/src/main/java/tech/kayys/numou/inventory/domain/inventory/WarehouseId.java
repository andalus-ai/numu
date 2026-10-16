package tech.kayys.syirkah.inventory.domain.inventory;

import java.util.Objects;
import java.util.UUID;

/** Identity of a physical or logical warehouse facility. */
public record WarehouseId(String value) {
    public WarehouseId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("WarehouseId must not be blank");
    }
    public static WarehouseId generate() { return new WarehouseId(UUID.randomUUID().toString()); }
    public static WarehouseId of(String v) { return new WarehouseId(v); }
}
