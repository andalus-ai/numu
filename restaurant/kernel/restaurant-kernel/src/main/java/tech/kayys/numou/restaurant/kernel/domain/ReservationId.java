package tech.kayys.numou.restaurant.kernel.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record ReservationId(UUID value) implements DomainId<UUID>, Serializable {

    public ReservationId {
        Objects.requireNonNull(value, "ReservationId value must not be null");
    }

    public static ReservationId of(UUID value) {
        return new ReservationId(value);
    }

    public static ReservationId of(String value) {
        return new ReservationId(UUID.fromString(value));
    }

    public static ReservationId generate() {
        return new ReservationId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
