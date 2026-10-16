package tech.kayys.numou.reservation.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResourcesAssigned(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID facilityId,
        List<UUID> resourceIds
) implements DomainEvent {

    public static ResourcesAssigned at(Instant now, UUID reservationId, UUID facilityId, List<UUID> resourceIds) {
        return new ResourcesAssigned(UUID.randomUUID(), now, reservationId, facilityId, resourceIds);
    }

    @Override
    public String eventType() {
        return "reservation.resources-assigned";
    }

    public UUID venueId() {
        return facilityId;
    }

    public List<UUID> tableIds() {
        return resourceIds;
    }
}
