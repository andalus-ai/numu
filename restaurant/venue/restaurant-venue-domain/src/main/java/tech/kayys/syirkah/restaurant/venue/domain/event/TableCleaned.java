package tech.kayys.syirkah.restaurant.venue.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record TableCleaned(
        UUID eventId,
        Instant occurredAt,
        UUID tableId,
        UUID venueId,
        int maxCovers) implements DomainEvent {

    public static TableCleaned at(Instant now, UUID tableId, UUID venueId, int maxCovers) {
        return new TableCleaned(UUID.randomUUID(), now, tableId, venueId, maxCovers);
    }

    @Override
    public String eventType() {
        return "restaurant.table.cleaned";
    }
}
