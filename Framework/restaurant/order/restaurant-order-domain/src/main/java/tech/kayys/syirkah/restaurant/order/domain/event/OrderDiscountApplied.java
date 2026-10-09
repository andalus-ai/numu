package tech.kayys.syirkah.restaurant.order.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record OrderDiscountApplied(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        UUID venueId,
        BigDecimal percent,
        String approvedBy) implements DomainEvent {

    public static OrderDiscountApplied at(Instant now, UUID orderId, UUID venueId, BigDecimal percent, String approvedBy) {
        return new OrderDiscountApplied(UUID.randomUUID(), now, orderId, venueId, percent, approvedBy);
    }

    @Override
    public String eventType() {
        return "restaurant.order.discount-applied";
    }
}
