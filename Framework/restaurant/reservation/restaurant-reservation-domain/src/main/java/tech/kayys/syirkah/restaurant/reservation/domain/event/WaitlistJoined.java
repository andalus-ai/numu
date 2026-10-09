package tech.kayys.syirkah.restaurant.reservation.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record WaitlistJoined(
        UUID eventId,
        Instant occurredAt,
        UUID entryId,
        UUID venueId,
        int partySize) implements DomainEvent {

    public static WaitlistJoined at(Instant now, UUID entryId, UUID venueId, int partySize) {
        return new WaitlistJoined(UUID.randomUUID(), now, entryId, venueId, partySize);
    }

    @Override
    public String eventType() {
        return "restaurant.waitlist.joined";
    }
}
