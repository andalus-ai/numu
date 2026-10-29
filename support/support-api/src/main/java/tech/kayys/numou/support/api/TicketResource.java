package tech.kayys.numou.support.api;

import tech.kayys.numou.support.application.api.command.CreatePortalTicketCommand;
import tech.kayys.numou.support.application.api.handler.CreatePortalTicketHandler;
import tech.kayys.numou.support.domain.ticket.TicketId;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;

import io.smallrye.mutiny.Uni;

@Path("/api/v1/support/tickets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TicketResource {

    private final CreatePortalTicketHandler createPortalTicketHandler;

    @Inject
    public TicketResource(CreatePortalTicketHandler createPortalTicketHandler) {
        this.createPortalTicketHandler = createPortalTicketHandler;
    }

    /**
     * Creates a ticket for the authenticated portal customer.
     *
     * Tenant and requester identity are derived from the authenticated actor context,
     * never accepted from the request body. Agent/system ticket creation must use a
     * separately authorized application entry point.
     */
    @POST
    public Uni<Response> createTicket(CreatePortalTicketCommand command) {
        return createPortalTicketHandler.handle(command)
                .onItem().transform(ticketId -> Response
                        .created(URI.create("/api/v1/support/tickets/" + ticketId.value()))
                        .entity(new CreateTicketResponse(ticketId))
                        .build());
    }

    public record CreateTicketResponse(String ticketId) {
        public CreateTicketResponse(TicketId ticketId) {
            this(ticketId.value().toString());
        }
    }
}
