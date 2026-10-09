package tech.kayys.syirkah.restaurant.reservation.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record ReservationRescheduled(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID venueId,
        Instant newStartsAt,
        int partySize) implements DomainEvent {

    public static ReservationRescheduled at(Instant now, UUID reservationId, UUID venueId, Instant newStartsAt, int partySize) {
        return new ReservationRescheduled(UUID.randomUUID(), now, reservationId, venueId, newStartsAt, partySize);
    }

    @Override
    public String eventType() {
        return "restaurant.reservation.rescheduled";
    }
}
