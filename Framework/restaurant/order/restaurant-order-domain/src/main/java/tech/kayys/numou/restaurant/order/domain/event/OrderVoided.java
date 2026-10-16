package tech.kayys.numou.restaurant.order.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record OrderVoided(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        UUID venueId,
        List<UUID> tableIds,
        String reason,
        String approvedBy) implements DomainEvent {

    public static OrderVoided at(Instant now, UUID orderId, UUID venueId, List<UUID> tableIds, String reason, String approvedBy) {
        return new OrderVoided(UUID.randomUUID(), now, orderId, venueId, tableIds, reason, approvedBy);
    }

    @Override
    public String eventType() {
        return "restaurant.order.voided";
    }
}
