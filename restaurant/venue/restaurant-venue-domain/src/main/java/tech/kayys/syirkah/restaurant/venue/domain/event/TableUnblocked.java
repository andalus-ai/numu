package tech.kayys.syirkah.restaurant.venue.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record TableUnblocked(
        UUID eventId,
        Instant occurredAt,
        UUID tableId,
        UUID venueId) implements DomainEvent {

    public static TableUnblocked at(Instant now, UUID tableId, UUID venueId) {
        return new TableUnblocked(UUID.randomUUID(), now, tableId, venueId);
    }

    @Override
    public String eventType() {
        return "restaurant.table.unblocked";
    }
}
