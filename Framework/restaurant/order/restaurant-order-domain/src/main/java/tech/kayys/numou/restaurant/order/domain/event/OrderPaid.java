package tech.kayys.numou.restaurant.order.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record OrderPaid(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        UUID venueId,
        List<UUID> tableIds,
        BigDecimal total,
        String currency,
        String paymentRef) implements DomainEvent {

    public static OrderPaid at(Instant now, UUID orderId, UUID venueId, List<UUID> tableIds, BigDecimal total, String currency, String paymentRef) {
        return new OrderPaid(UUID.randomUUID(), now, orderId, venueId, tableIds, total, currency, paymentRef);
    }

    @Override
    public String eventType() {
        return "restaurant.order.paid";
    }
}
