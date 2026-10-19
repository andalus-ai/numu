package tech.kayys.numou.fms.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record RouteId(UUID value) implements DomainId<UUID>, Serializable {

    public RouteId {
        Objects.requireNonNull(value, "RouteId value cannot be null");
    }

    public static RouteId of(UUID value) {
        return new RouteId(value);
    }

    public static RouteId generate() {
        return new RouteId(UUID.randomUUID());
    }

    public static RouteId fromString(String value) {
        return new RouteId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "RouteId{" + value + "}";
    }
}
