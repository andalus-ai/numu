package tech.kayys.numou.reservation.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record WaitlistCheckedIn(
        UUID eventId,
        Instant occurredAt,
        UUID waitlistId,
        UUID facilityId
) implements DomainEvent {

    public static WaitlistCheckedIn at(Instant now, UUID waitlistId, UUID facilityId) {
        return new WaitlistCheckedIn(UUID.randomUUID(), now, waitlistId, facilityId);
    }

    @Override
    public String eventType() {
        return "reservation.waitlist.checked-in";
    }

    public UUID venueId() {
        return facilityId;
    }
}
