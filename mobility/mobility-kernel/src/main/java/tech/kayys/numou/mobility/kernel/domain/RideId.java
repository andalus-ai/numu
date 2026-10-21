package tech.kayys.numou.mobility.kernel.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record RideId(UUID value) implements DomainId<UUID>, Serializable {

    public RideId {
        Objects.requireNonNull(value, "RideId value must not be null");
    }

    public static RideId of(UUID value) {
        return new RideId(value);
    }

    public static RideId of(String value) {
        return new RideId(UUID.fromString(value));
    }

    public static RideId generate() {
        return new RideId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
