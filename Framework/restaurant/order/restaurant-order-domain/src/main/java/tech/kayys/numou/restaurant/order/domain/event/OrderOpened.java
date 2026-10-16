package tech.kayys.numou.restaurant.order.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record OrderOpened(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        UUID venueId,
        List<UUID> tableIds,
        int covers) implements DomainEvent {

    public static OrderOpened at(Instant now, UUID orderId, UUID venueId, List<UUID> tableIds, int covers) {
        return new OrderOpened(UUID.randomUUID(), now, orderId, venueId, tableIds, covers);
    }

    @Override
    public String eventType() {
        return "restaurant.order.opened";
    }
}
