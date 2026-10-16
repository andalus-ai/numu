package tech.kayys.syirkah.restaurant.order.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record OrderTableTransferred(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        UUID venueId,
        List<UUID> fromTableIds,
        List<UUID> toTableIds) implements DomainEvent {

    public static OrderTableTransferred at(Instant now, UUID orderId, UUID venueId, List<UUID> fromTableIds, List<UUID> toTableIds) {
        return new OrderTableTransferred(UUID.randomUUID(), now, orderId, venueId, fromTableIds, toTableIds);
    }

    @Override
    public String eventType() {
        return "restaurant.order.table-transferred";
    }
}
