package tech.kayys.numou.support.domain.ticket.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.support.domain.ticket.TicketComment;
import tech.kayys.numou.support.domain.ticket.TicketId;

import java.time.Instant;
import java.util.UUID;

public record TicketCommentAdded(
        UUID eventId,
        Instant occurredAt,
        TicketId ticketId,
        TicketComment comment
) implements DomainEvent {
    @Override
    public String eventType() {
        return "support.ticket-comment-added";
    }
}
