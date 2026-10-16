package tech.kayys.syirkah.restaurant.kitchen.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record ItemRestored(
        UUID eventId,
        Instant occurredAt,
        UUID venueId,
        UUID menuItemId) implements DomainEvent {

    public static ItemRestored at(Instant now, UUID venueId, UUID menuItemId) {
        return new ItemRestored(UUID.randomUUID(), now, venueId, menuItemId);
    }

    @Override
    public String eventType() {
        return "restaurant.kitchen.item.restored";
    }
}
