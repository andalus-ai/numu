package tech.kayys.numou.fms.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record GeofenceId(UUID value) implements DomainId<UUID>, Serializable {

    public GeofenceId {
        Objects.requireNonNull(value, "GeofenceId value cannot be null");
    }

    public static GeofenceId of(UUID value) {
        return new GeofenceId(value);
    }

    public static GeofenceId generate() {
        return new GeofenceId(UUID.randomUUID());
    }

    public static GeofenceId fromString(String value) {
        return new GeofenceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "GeofenceId{" + value + "}";
    }
}
