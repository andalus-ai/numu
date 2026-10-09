package tech.kayys.syirkah.restaurant.reservation.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record ReservationRequested(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID venueId,
        int partySize,
        Instant startsAt,
        String status) implements DomainEvent {

    public static ReservationRequested at(Instant now, UUID reservationId, UUID venueId, int partySize, Instant startsAt, String status) {
        return new ReservationRequested(UUID.randomUUID(), now, reservationId, venueId, partySize, startsAt, status);
    }

    @Override
    public String eventType() {
        return "restaurant.reservation.requested";
    }
}
