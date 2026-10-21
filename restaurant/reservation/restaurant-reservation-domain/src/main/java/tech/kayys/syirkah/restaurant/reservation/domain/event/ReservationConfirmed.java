package tech.kayys.syirkah.restaurant.reservation.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record ReservationConfirmed(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID venueId,
        Instant startsAt) implements DomainEvent {

    public static ReservationConfirmed at(Instant now, UUID reservationId, UUID venueId, Instant startsAt) {
        return new ReservationConfirmed(UUID.randomUUID(), now, reservationId, venueId, startsAt);
    }

    @Override
    public String eventType() {
        return "restaurant.reservation.confirmed";
    }
}
