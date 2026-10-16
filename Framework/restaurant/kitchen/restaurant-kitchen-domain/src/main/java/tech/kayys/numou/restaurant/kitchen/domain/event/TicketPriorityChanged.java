package tech.kayys.numou.restaurant.kitchen.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record TicketPriorityChanged(
        UUID eventId,
        Instant occurredAt,
        UUID ticketId,
        UUID orderId,
        String priority) implements DomainEvent {

    public static TicketPriorityChanged at(Instant now, UUID ticketId, UUID orderId, String priority) {
        return new TicketPriorityChanged(UUID.randomUUID(), now, ticketId, orderId, priority);
    }

    @Override
    public String eventType() {
        return "restaurant.kitchen.ticket.priority-changed";
    }
}
