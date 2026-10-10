package tech.kayys.syirkah.reservation.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record WaitlistJoined(
        UUID eventId,
        Instant occurredAt,
        UUID waitlistId,
        UUID facilityId,
        int partySize
) implements DomainEvent {

    public static WaitlistJoined at(Instant now, UUID waitlistId, UUID facilityId, int partySize) {
        return new WaitlistJoined(UUID.randomUUID(), now, waitlistId, facilityId, partySize);
    }

    @Override
    public String eventType() {
        return "reservation.waitlist.joined";
    }

    public UUID venueId() {
        return facilityId;
    }
}
