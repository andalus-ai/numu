package tech.kayys.syirkah.reservation.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ReservationCompleted(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID facilityId
) implements DomainEvent {

    public static ReservationCompleted at(Instant now, UUID reservationId, UUID facilityId) {
        return new ReservationCompleted(UUID.randomUUID(), now, reservationId, facilityId);
    }

    @Override
    public String eventType() {
        return "reservation.completed";
    }

    public UUID venueId() {
        return facilityId;
    }
}
