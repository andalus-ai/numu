package tech.kayys.numou.support.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.support.domain.ticket.Requester;
import tech.kayys.numou.support.domain.ticket.Ticket;
import tech.kayys.numou.support.domain.ticket.TicketId;

import java.util.List;
import java.util.Optional;

public interface TicketRepository {

    Uni<Ticket> save(Ticket ticket);

    Uni<Optional<Ticket>> findById(TicketId ticketId);

    Uni<List<Ticket>> findByTenantAndRequester(java.util.UUID tenantId, Requester requester, int offset, int limit);
}
