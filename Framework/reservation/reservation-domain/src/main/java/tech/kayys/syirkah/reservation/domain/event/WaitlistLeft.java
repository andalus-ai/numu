package tech.kayys.syirkah.reservation.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record WaitlistLeft(
        UUID eventId,
        Instant occurredAt,
        UUID waitlistId,
        UUID facilityId,
        String reason
) implements DomainEvent {

    public static WaitlistLeft at(Instant now, UUID waitlistId, UUID facilityId, String reason) {
        return new WaitlistLeft(UUID.randomUUID(), now, waitlistId, facilityId, reason);
    }

    @Override
    public String eventType() {
        return "reservation.waitlist.left";
    }

    public UUID venueId() {
        return facilityId;
    }
}
