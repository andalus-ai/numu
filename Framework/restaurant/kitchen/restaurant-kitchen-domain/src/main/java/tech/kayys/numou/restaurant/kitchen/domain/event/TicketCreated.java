package tech.kayys.numou.restaurant.kitchen.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record TicketCreated(
        UUID eventId,
        Instant occurredAt,
        UUID ticketId,
        UUID orderId,
        UUID venueId,
        UUID stationId,
        int course,
        int itemCount,
        Instant promisedAt) implements DomainEvent {

    public static TicketCreated at(Instant now, UUID ticketId, UUID orderId, UUID venueId, UUID stationId, int course, int itemCount, Instant promisedAt) {
        return new TicketCreated(UUID.randomUUID(), now, ticketId, orderId, venueId, stationId, course, itemCount, promisedAt);
    }

    @Override
    public String eventType() {
        return "restaurant.kitchen.ticket.created";
    }
}
