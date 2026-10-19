package tech.kayys.numou.reservation.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record ReservationId(UUID value) implements DomainId<UUID> {
    public ReservationId {
        Objects.requireNonNull(value, "ReservationId value cannot be null");
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
}
