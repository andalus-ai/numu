package tech.kayys.syirkah.restaurant.kitchen.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record ItemEightySixed(
        UUID eventId,
        Instant occurredAt,
        UUID venueId,
        UUID menuItemId,
        String reason,
        Instant until) implements DomainEvent {

    public static ItemEightySixed at(Instant now, UUID venueId, UUID menuItemId, String reason, Instant until) {
        return new ItemEightySixed(UUID.randomUUID(), now, venueId, menuItemId, reason, until);
    }

    @Override
    public String eventType() {
        return "restaurant.kitchen.item.eighty-sixed";
    }
}
