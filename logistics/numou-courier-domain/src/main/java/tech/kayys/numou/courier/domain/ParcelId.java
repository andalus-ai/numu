package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Stable identifier for an individual physical piece within a shipment. */
public record ParcelId(UUID value) implements DomainId<UUID>, Serializable {
    public ParcelId {
        Objects.requireNonNull(value, "ParcelId value must not be null");
    }
    public static ParcelId of(UUID value) { return new ParcelId(value); }
    public static ParcelId of(String value) { return new ParcelId(UUID.fromString(value)); }
    public static ParcelId generate() { return new ParcelId(UUID.randomUUID()); }
    @Override public String toString() { return value.toString(); }
}