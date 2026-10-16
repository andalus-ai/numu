package tech.kayys.syirkah.inventory.domain.inventory;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of an inventory item. */
public record ItemId(String value) {
    public ItemId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("ItemId must not be blank");
    }
    public static ItemId generate() { return new ItemId(UUID.randomUUID().toString()); }
    public static ItemId of(String v) { return new ItemId(v); }
}
