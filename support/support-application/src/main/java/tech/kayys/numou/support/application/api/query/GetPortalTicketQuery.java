package tech.kayys.numou.support.application.api.query;

import tech.kayys.numou.foundation.application.query.Query;
import tech.kayys.numou.support.domain.ticket.TicketId;

import java.util.Objects;

public record GetPortalTicketQuery(TicketId ticketId) implements Query {
    public GetPortalTicketQuery {
        Objects.requireNonNull(ticketId, "ticketId cannot be null");
    }
}
