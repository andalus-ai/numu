package tech.kayys.numou.restaurant.venue.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record TableBlocked(
        UUID eventId,
        Instant occurredAt,
        UUID tableId,
        UUID venueId,
        String reason) implements DomainEvent {

    public static TableBlocked at(Instant now, UUID tableId, UUID venueId, String reason) {
        return new TableBlocked(UUID.randomUUID(), now, tableId, venueId, reason);
    }

    @Override
    public String eventType() {
        return "restaurant.table.blocked";
    }
}
