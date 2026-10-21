package tech.kayys.syirkah.restaurant.kitchen.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record TicketBumped(
        UUID eventId,
        Instant occurredAt,
        UUID ticketId,
        UUID orderId,
        UUID venueId,
        long cookSeconds) implements DomainEvent {

    public static TicketBumped at(Instant now, UUID ticketId, UUID orderId, UUID venueId, long cookSeconds) {
        return new TicketBumped(UUID.randomUUID(), now, ticketId, orderId, venueId, cookSeconds);
    }

    @Override
    public String eventType() {
        return "restaurant.kitchen.ticket.bumped";
    }
}
