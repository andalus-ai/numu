package tech.kayys.syirkah.restaurant.kitchen.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record TicketReady(
        UUID eventId,
        Instant occurredAt,
        UUID ticketId,
        UUID orderId,
        UUID venueId,
        UUID stationId,
        int course) implements DomainEvent {

    public static TicketReady at(Instant now, UUID ticketId, UUID orderId, UUID venueId, UUID stationId, int course) {
        return new TicketReady(UUID.randomUUID(), now, ticketId, orderId, venueId, stationId, course);
    }

    @Override
    public String eventType() {
        return "restaurant.kitchen.ticket.ready";
    }
}
