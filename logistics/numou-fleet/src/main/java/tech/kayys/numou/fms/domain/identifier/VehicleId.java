package tech.kayys.numou.fms.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record VehicleId(UUID value) implements DomainId<UUID>, Serializable {

    public VehicleId {
        Objects.requireNonNull(value, "VehicleId value cannot be null");
    }

    public static VehicleId of(UUID value) {
        return new VehicleId(value);
    }

    public static VehicleId generate() {
        return new VehicleId(UUID.randomUUID());
    }

    public static VehicleId fromString(String value) {
        return new VehicleId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "VehicleId{" + value + "}";
    }
}
