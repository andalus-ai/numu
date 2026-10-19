package tech.kayys.numou.restaurant.order.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record OrderLineVoided(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        UUID venueId,
        UUID lineId,
        String reason,
        String approvedBy) implements DomainEvent {

    public static OrderLineVoided at(Instant now, UUID orderId, UUID venueId, UUID lineId, String reason, String approvedBy) {
        return new OrderLineVoided(UUID.randomUUID(), now, orderId, venueId, lineId, reason, approvedBy);
    }

    @Override
    public String eventType() {
        return "restaurant.order.line-voided";
    }
}
