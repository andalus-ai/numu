package tech.kayys.numou.restaurant.kitchen.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record TicketItemReady(
        UUID eventId,
        Instant occurredAt,
        UUID ticketId,
        UUID orderId,
        UUID lineId) implements DomainEvent {

    public static TicketItemReady at(Instant now, UUID ticketId, UUID orderId, UUID lineId) {
        return new TicketItemReady(UUID.randomUUID(), now, ticketId, orderId, lineId);
    }

    @Override
    public String eventType() {
        return "restaurant.kitchen.item.ready";
    }
}
