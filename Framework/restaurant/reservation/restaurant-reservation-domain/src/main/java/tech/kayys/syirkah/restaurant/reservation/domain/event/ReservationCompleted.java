package tech.kayys.syirkah.restaurant.reservation.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record ReservationCompleted(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID venueId) implements DomainEvent {

    public static ReservationCompleted at(Instant now, UUID reservationId, UUID venueId) {
        return new ReservationCompleted(UUID.randomUUID(), now, reservationId, venueId);
    }

    @Override
    public String eventType() {
        return "restaurant.reservation.completed";
    }
}
