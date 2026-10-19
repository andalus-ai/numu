package tech.kayys.numou.reservation.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ReservationRequested(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID facilityId,
        int partySize,
        Instant startsAt,
        String status
) implements DomainEvent {

    public static ReservationRequested at(Instant now, UUID reservationId, UUID facilityId, int partySize, Instant startsAt, String status) {
        return new ReservationRequested(UUID.randomUUID(), now, reservationId, facilityId, partySize, startsAt, status);
    }

    @Override
    public String eventType() {
        return "reservation.requested";
    }

    // Compatibility method for restaurant contexts
    public UUID venueId() {
        return facilityId;
    }
}
