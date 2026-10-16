package tech.kayys.syirkah.inventory.domain.reservation;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identity of an inventory reservation.
 */
public record ReservationId(String value) {
    public ReservationId {
        Objects.requireNonNull(value, "value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ReservationId cannot be blank");
        }
    }

    public static ReservationId generate() {
        return new ReservationId(UUID.randomUUID().toString());
    }

    public static ReservationId of(String value) {
        return new ReservationId(value);
    }
}
