package tech.kayys.numou.finance.treasury.adapter.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.finance.treasury.application.api.TreasuryService;
import tech.kayys.numou.finance.treasury.application.api.command.CloseDrawerCommand;
import tech.kayys.numou.finance.treasury.application.api.command.OpenDrawerCommand;
import tech.kayys.numou.finance.treasury.domain.identifier.DrawerSessionId;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

@Path("/api/v1/finance/treasury")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TreasuryResource {

    @Inject
    TreasuryService treasuryService;

    @POST
    @Path("/sessions/open")
    public CompletionStage<Response> openSession(OpenDrawerCommand command) {
        return treasuryService.openSession(command)
                .thenApply(id -> Response.ok(id).build());
    }

    @POST
    @Path("/sessions/{sessionId}/close")
    public CompletionStage<Response> closeSession(
            @PathParam("sessionId") UUID sessionId,
            CloseDrawerCommand request) {
        CloseDrawerCommand command = new CloseDrawerCommand(
                DrawerSessionId.of(sessionId),
                request.actualCountedCash(),
                request.notes()
        );
        return treasuryService.closeSession(command)
                .thenApply(zReport -> Response.ok(zReport).build());
    }

    @GET
    @Path("/sessions/{sessionId}/z-report")
    public CompletionStage<Response> getZReport(@PathParam("sessionId") UUID sessionId) {
        return treasuryService.getZReport(DrawerSessionId.of(sessionId))
                .thenApply(zReport -> Response.ok(zReport).build());
    }
}
