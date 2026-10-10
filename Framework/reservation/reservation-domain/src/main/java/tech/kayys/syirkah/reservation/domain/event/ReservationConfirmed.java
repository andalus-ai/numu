package tech.kayys.syirkah.reservation.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ReservationConfirmed(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID facilityId,
        Instant startsAt
) implements DomainEvent {

    public static ReservationConfirmed at(Instant now, UUID reservationId, UUID facilityId, Instant startsAt) {
        return new ReservationConfirmed(UUID.randomUUID(), now, reservationId, facilityId, startsAt);
    }

    @Override
    public String eventType() {
        return "reservation.confirmed";
    }

    public UUID venueId() {
        return facilityId;
    }
}
