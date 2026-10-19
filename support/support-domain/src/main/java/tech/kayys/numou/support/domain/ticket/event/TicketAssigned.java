package tech.kayys.numou.support.domain.ticket.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.support.domain.ticket.TicketId;

import java.time.Instant;
import java.util.UUID;

public record TicketAssigned(
        UUID eventId,
        Instant occurredAt,
        TicketId ticketId,
        UserId agentId
) implements DomainEvent {
    @Override
    public String eventType() {
        return "support.ticket-assigned";
    }
}
