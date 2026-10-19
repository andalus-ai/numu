package tech.kayys.numou.restaurant.kitchen.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record TicketItemVoided(
        UUID eventId,
        Instant occurredAt,
        UUID ticketId,
        UUID orderId,
        UUID lineId,
        String reason) implements DomainEvent {

    public static TicketItemVoided at(Instant now, UUID ticketId, UUID orderId, UUID lineId, String reason) {
        return new TicketItemVoided(UUID.randomUUID(), now, ticketId, orderId, lineId, reason);
    }

    @Override
    public String eventType() {
        return "restaurant.kitchen.item.voided";
    }
}
