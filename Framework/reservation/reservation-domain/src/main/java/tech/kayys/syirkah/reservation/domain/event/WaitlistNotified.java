package tech.kayys.syirkah.reservation.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record WaitlistNotified(
        UUID eventId,
        Instant occurredAt,
        UUID waitlistId,
        UUID facilityId,
        Instant holdUntil
) implements DomainEvent {

    public static WaitlistNotified at(Instant now, UUID waitlistId, UUID facilityId, Instant holdUntil) {
        return new WaitlistNotified(UUID.randomUUID(), now, waitlistId, facilityId, holdUntil);
    }

    @Override
    public String eventType() {
        return "reservation.waitlist.notified";
    }

    public UUID venueId() {
        return facilityId;
    }
}
