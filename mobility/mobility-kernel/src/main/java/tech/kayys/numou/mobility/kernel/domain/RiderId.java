package tech.kayys.numou.mobility.kernel.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record RiderId(UUID value) implements DomainId<UUID>, Serializable {

    public RiderId {
        Objects.requireNonNull(value, "RiderId value must not be null");
    }

    public static RiderId of(UUID value) {
        return new RiderId(value);
    }

    public static RiderId of(String value) {
        return new RiderId(UUID.fromString(value));
    }

    public static RiderId generate() {
        return new RiderId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
