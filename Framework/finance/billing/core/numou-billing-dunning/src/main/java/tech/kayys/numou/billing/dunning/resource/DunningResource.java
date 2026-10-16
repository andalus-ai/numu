package tech.kayys.numou.billing.dunning.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.billing.dunning.model.DunningCase;
import tech.kayys.numou.billing.dunning.service.DunningService;

import java.math.BigDecimal;
import java.util.List;

@Path("/api/v1/billing/dunning")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DunningResource {

    @Inject
    DunningService dunningService;

    @GET
    @Path("/cases")
    public List<DunningCase> listCases() {
        return dunningService.listActiveCases();
    }

    @GET
    @Path("/cases/{id}")
    public Response getCase(@PathParam("id") String id) {
        return dunningService.getCase(id)
            .map(Response::ok)
            .orElse(Response.status(Response.Status.NOT_FOUND))
            .build();
    }

    public record OpenCaseRequest(
        String scheduleId,
        String customerId,
        String invoiceId,
        BigDecimal amount,
        String currency
    ) {}

    @POST
    @Path("/cases")
    public Response openCase(OpenCaseRequest request) {
        DunningCase dcase = dunningService.openCase(
            request.scheduleId(),
            request.customerId(),
            request.invoiceId(),
            request.amount(),
            request.currency()
        );
        return Response.status(Response.Status.CREATED).entity(dcase).build();
    }

    @POST
    @Path("/cases/{id}/escalate")
    public Response escalateCase(@PathParam("id") String id) {
        return dunningService.getCase(id)
            .map(dcase -> {
                dunningService.processCase(dcase);
                return Response.ok(dcase).build();
            })
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Path("/cases/{id}/resolve")
    public Response resolveCase(@PathParam("id") String id, @QueryParam("notes") String notes) {
        dunningService.resolveCase(id, notes != null ? notes : "Manually resolved");
        return Response.ok().build();
    }
}
