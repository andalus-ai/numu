package tech.kayys.syirkah.restaurant.venue.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record MenuArchived(
        UUID eventId,
        Instant occurredAt,
        UUID menuId,
        UUID venueId) implements DomainEvent {

    public static MenuArchived at(Instant now, UUID menuId, UUID venueId) {
        return new MenuArchived(UUID.randomUUID(), now, menuId, venueId);
    }

    @Override
    public String eventType() {
        return "restaurant.menu.archived";
    }
}
