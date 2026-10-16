package tech.kayys.numou.restaurant.kitchen.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record TicketRecalled(
        UUID eventId,
        Instant occurredAt,
        UUID ticketId,
        UUID orderId) implements DomainEvent {

    public static TicketRecalled at(Instant now, UUID ticketId, UUID orderId) {
        return new TicketRecalled(UUID.randomUUID(), now, ticketId, orderId);
    }

    @Override
    public String eventType() {
        return "restaurant.kitchen.ticket.recalled";
    }
}
