package tech.kayys.numou.support.domain.ticket.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.support.domain.ticket.TicketId;
import tech.kayys.numou.support.domain.ticket.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public record TicketStatusChanged(
        UUID eventId,
        Instant occurredAt,
        TicketId ticketId,
        TicketStatus previousStatus,
        TicketStatus newStatus
) implements DomainEvent {
    @Override
    public String eventType() {
        return "support.ticket-status-changed";
    }
}
