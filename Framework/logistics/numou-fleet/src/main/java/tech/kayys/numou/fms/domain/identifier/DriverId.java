package tech.kayys.numou.fms.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record DriverId(UUID value) implements DomainId<UUID>, Serializable {

    public DriverId {
        Objects.requireNonNull(value, "DriverId value cannot be null");
    }

    public static DriverId of(UUID value) {
        return new DriverId(value);
    }

    public static DriverId generate() {
        return new DriverId(UUID.randomUUID());
    }

    public static DriverId fromString(String value) {
        return new DriverId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "DriverId{" + value + "}";
    }
}
