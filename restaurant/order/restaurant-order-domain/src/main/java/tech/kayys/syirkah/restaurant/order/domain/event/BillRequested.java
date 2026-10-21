package tech.kayys.syirkah.restaurant.order.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record BillRequested(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        UUID venueId,
        BigDecimal total,
        String currency) implements DomainEvent {

    public static BillRequested at(Instant now, UUID orderId, UUID venueId, BigDecimal total, String currency) {
        return new BillRequested(UUID.randomUUID(), now, orderId, venueId, total, currency);
    }

    @Override
    public String eventType() {
        return "restaurant.order.bill-requested";
    }
}
