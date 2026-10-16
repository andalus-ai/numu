package tech.kayys.syirkah.inventory.domain.inventory;

import java.util.Objects;
import java.util.UUID;

/** Unique identifier for an immutable stock movement event. */
public record MovementId(String value) {
    public MovementId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("MovementId must not be blank");
    }
    public static MovementId generate() { return new MovementId(UUID.randomUUID().toString()); }
    public static MovementId of(String v) { return new MovementId(v); }
}
