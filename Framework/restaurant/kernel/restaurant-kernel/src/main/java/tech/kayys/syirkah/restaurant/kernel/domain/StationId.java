package tech.kayys.syirkah.restaurant.kernel.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record StationId(UUID value) implements DomainId<UUID>, Serializable {

    public StationId {
        Objects.requireNonNull(value, "StationId value must not be null");
    }

    public static StationId of(UUID value) {
        return new StationId(value);
    }

    public static StationId of(String value) {
        return new StationId(UUID.fromString(value));
    }

    public static StationId generate() {
        return new StationId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
