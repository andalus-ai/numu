package tech.kayys.syirkah.restaurant.reservation.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record WaitlistLeft(
        UUID eventId,
        Instant occurredAt,
        UUID entryId,
        UUID venueId,
        String reason) implements DomainEvent {

    public static WaitlistLeft at(Instant now, UUID entryId, UUID venueId, String reason) {
        return new WaitlistLeft(UUID.randomUUID(), now, entryId, venueId, reason);
    }

    @Override
    public String eventType() {
        return "restaurant.waitlist.left";
    }
}
