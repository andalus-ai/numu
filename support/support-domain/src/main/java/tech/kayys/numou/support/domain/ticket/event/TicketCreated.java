package tech.kayys.numou.support.domain.ticket.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.support.domain.ticket.TicketId;
import tech.kayys.numou.support.domain.ticket.TicketPriority;
import tech.kayys.numou.support.domain.ticket.TicketType;

import java.time.Instant;
import java.util.UUID;

public record TicketCreated(
        UUID eventId,
        Instant occurredAt,
        TicketId ticketId,
        TicketType type,
        TicketPriority priority
) implements DomainEvent {
    @Override
    public String eventType() {
        return "support.ticket-created";
    }
}
