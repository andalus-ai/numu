package tech.kayys.numou.reservation.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationCheckedIn(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID facilityId,
        List<UUID> resourceIds
) implements DomainEvent {

    public static ReservationCheckedIn at(Instant now, UUID reservationId, UUID facilityId, List<UUID> resourceIds) {
        return new ReservationCheckedIn(UUID.randomUUID(), now, reservationId, facilityId, resourceIds);
    }

    @Override
    public String eventType() {
        return "reservation.checked-in";
    }

    public UUID venueId() {
        return facilityId;
    }

    public List<UUID> tableIds() {
        return resourceIds;
    }
}
