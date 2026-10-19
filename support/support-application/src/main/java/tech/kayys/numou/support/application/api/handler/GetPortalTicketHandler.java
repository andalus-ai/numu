package tech.kayys.numou.support.application.api.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.support.application.api.SupportActorProvider;
import tech.kayys.numou.support.application.api.TicketAccessPolicy;
import tech.kayys.numou.support.application.api.TicketResponse;
import tech.kayys.numou.support.application.api.query.GetPortalTicketQuery;
import tech.kayys.numou.support.application.port.TicketRepository;

import java.util.Objects;

public final class GetPortalTicketHandler {

    private final TicketRepository ticketRepository;
    private final SupportActorProvider actorProvider;
    private final TicketAccessPolicy accessPolicy;

    public GetPortalTicketHandler(TicketRepository ticketRepository, SupportActorProvider actorProvider,
                                  TicketAccessPolicy accessPolicy) {
        this.ticketRepository = Objects.requireNonNull(ticketRepository);
        this.actorProvider = Objects.requireNonNull(actorProvider);
        this.accessPolicy = Objects.requireNonNull(accessPolicy);
    }

    public Uni<TicketResponse> handle(GetPortalTicketQuery query) {
        Objects.requireNonNull(query, "query cannot be null");
        return actorProvider.currentActor().flatMap(actor ->
                accessPolicy.canRead(query.ticketId(), actor).flatMap(allowed -> {
                    if (!allowed) {
                        return Uni.createFrom().failure(
                                new InvalidStateException("Ticket not found"));
                    }
                    return ticketRepository.findById(query.ticketId()).flatMap(optional ->
                            optional.filter(ticket -> ticket.tenantId().equals(actor.tenantId()))
                                    .map(ticket -> Uni.createFrom().item(
                                            TicketResponse.fromDomain(ticket, actor.type())))
                                    .orElseGet(() -> Uni.createFrom().failure(
                                            new InvalidStateException("Ticket not found"))));
                }));
    }
}
