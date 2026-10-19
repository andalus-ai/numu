package tech.kayys.numou.restaurant.venue.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record MenuPublished(
        UUID eventId,
        Instant occurredAt,
        UUID menuId,
        UUID venueId) implements DomainEvent {

    public static MenuPublished at(Instant now, UUID menuId, UUID venueId) {
        return new MenuPublished(UUID.randomUUID(), now, menuId, venueId);
    }

    @Override
    public String eventType() {
        return "restaurant.menu.published";
    }
}
