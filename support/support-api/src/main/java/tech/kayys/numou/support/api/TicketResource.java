package tech.kayys.numou.support.api;

import tech.kayys.numou.support.application.api.command.CreatePortalTicketCommand;
import tech.kayys.numou.support.application.api.handler.CreatePortalTicketHandler;
import tech.kayys.numou.support.application.api.handler.GetPortalTicketHandler;
import tech.kayys.numou.support.application.api.query.GetPortalTicketQuery;
import tech.kayys.numou.support.application.api.TicketResponse;
import tech.kayys.numou.support.domain.ticket.TicketId;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.UUID;

import io.smallrye.mutiny.Uni;

@Path("/api/v1/support/tickets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TicketResource {

    private final CreatePortalTicketHandler createPortalTicketHandler;
    private final GetPortalTicketHandler getPortalTicketHandler;

    @Inject
    public TicketResource(CreatePortalTicketHandler createPortalTicketHandler,
                          GetPortalTicketHandler getPortalTicketHandler) {
        this.createPortalTicketHandler = createPortalTicketHandler;
        this.getPortalTicketHandler = getPortalTicketHandler;
    }

    /**
     * Creates a ticket for an authenticated customer or partner portal actor.
     * Tenant and requester identity are derived from the verified actor context,
     * never accepted from the request body.
     */
    @POST
    public Uni<Response> createTicket(CreatePortalTicketCommand command) {
        return createPortalTicketHandler.handle(command)
                .onItem().transform(ticketId -> Response
                        .created(URI.create("/api/v1/support/tickets/" + ticketId.value()))
                        .entity(new CreateTicketResponse(ticketId))
                        .build());
    }

    /**
     * Returns a DTO-only portal view. The handler applies actor and requester
     * authorization and deliberately uses the same not-found result for
     * inaccessible and missing tickets.
     */
    @GET
    @Path("/{ticketId}")
    public Uni<TicketResponse> getPortalTicket(@PathParam("ticketId") String ticketId) {
        final TicketId id;
        try {
            id = TicketId.of(UUID.fromString(ticketId));
        } catch (IllegalArgumentException invalidId) {
            throw new NotFoundException("Ticket not found");
        }
        return getPortalTicketHandler.handle(new GetPortalTicketQuery(id));
    }

    public record CreateTicketResponse(String ticketId) {
        public CreateTicketResponse(TicketId ticketId) {
            this(ticketId.value().toString());
        }
    }
}
