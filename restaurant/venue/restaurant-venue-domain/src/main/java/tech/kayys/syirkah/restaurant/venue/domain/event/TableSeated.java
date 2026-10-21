package tech.kayys.syirkah.restaurant.venue.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record TableSeated(
        UUID eventId,
        Instant occurredAt,
        UUID tableId,
        UUID venueId,
        UUID orderId,
        int covers) implements DomainEvent {

    public static TableSeated at(Instant now, UUID tableId, UUID venueId, UUID orderId, int covers) {
        return new TableSeated(UUID.randomUUID(), now, tableId, venueId, orderId, covers);
    }

    @Override
    public String eventType() {
        return "restaurant.table.seated";
    }
}
