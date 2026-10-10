package tech.kayys.syirkah.reservation.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ReservationRescheduled(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID facilityId,
        Instant newStartsAt,
        int newPartySize
) implements DomainEvent {

    public static ReservationRescheduled at(Instant now, UUID reservationId, UUID facilityId, Instant newStartsAt, int newPartySize) {
        return new ReservationRescheduled(UUID.randomUUID(), now, reservationId, facilityId, newStartsAt, newPartySize);
    }

    @Override
    public String eventType() {
        return "reservation.rescheduled";
    }

    public UUID venueId() {
        return facilityId;
    }
}
