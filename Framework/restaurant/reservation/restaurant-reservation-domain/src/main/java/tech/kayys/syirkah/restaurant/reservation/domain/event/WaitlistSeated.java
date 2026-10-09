package tech.kayys.syirkah.restaurant.reservation.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record WaitlistSeated(
        UUID eventId,
        Instant occurredAt,
        UUID entryId,
        UUID venueId) implements DomainEvent {

    public static WaitlistSeated at(Instant now, UUID entryId, UUID venueId) {
        return new WaitlistSeated(UUID.randomUUID(), now, entryId, venueId);
    }

    @Override
    public String eventType() {
        return "restaurant.waitlist.seated";
    }
}
