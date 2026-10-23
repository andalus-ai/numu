package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Stable identifier for a booked or draft shipment. */
public record ShipmentId(UUID value) implements DomainId<UUID>, Serializable {
    public ShipmentId {
        Objects.requireNonNull(value, "ShipmentId value must not be null");
    }
    public static ShipmentId of(UUID value) { return new ShipmentId(value); }
    public static ShipmentId of(String value) { return new ShipmentId(UUID.fromString(value)); }
    public static ShipmentId generate() { return new ShipmentId(UUID.randomUUID()); }
    @Override public String toString() { return value.toString(); }
}