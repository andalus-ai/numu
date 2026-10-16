package tech.kayys.syirkah.accounting.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.syirkah.accounting.application.port.FiscalPeriodRepository;
import tech.kayys.syirkah.accounting.application.service.FiscalPeriodService;
import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.identifier.FiscalPeriodId;
import tech.kayys.syirkah.accounting.domain.model.FiscalPeriod;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/accounting/periods")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Fiscal Period API", description = "Endpoints for fiscal period management and year-end closing")
public class FiscalPeriodResource {

    @Inject
    FiscalPeriodService fiscalPeriodService;

    @Inject
    FiscalPeriodRepository fiscalPeriodRepository;

    
public record YearEndClosingRequest(int year, UUID retainedEarningsAccountId, String closedBy) {}

    @GET
    @Operation(summary = "List all fiscal periods")
    public Uni<List<FiscalPeriod>> listPeriods() {
        return fiscalPeriodRepository.findAll();
    }

    @POST
    @Path("/{id}/close")
    @Operation(summary = "Close a fiscal period")
    public Uni<Response> closePeriod(@PathParam("id") UUID id) {
        return fiscalPeriodService.closePeriod(FiscalPeriodId.of(id))
                .map(v -> Response.ok().build());
    }

    @POST
    @Path("/{id}/lock")
    @Operation(summary = "Lock a fiscal period (audit freeze)")
    public Uni<Response> lockPeriod(@PathParam("id") UUID id) {
        return fiscalPeriodService.lockPeriod(FiscalPeriodId.of(id))
                .map(v -> Response.ok().build());
    }

    @POST
    @Path("/{id}/reopen")
    @Operation(summary = "Reopen a closed fiscal period")
    public Uni<Response> reopenPeriod(@PathParam("id") UUID id) {
        return fiscalPeriodService.reopenPeriod(FiscalPeriodId.of(id))
                .map(v -> Response.ok().build());
    }

    @POST
    @Path("/year-end-close")
    @Operation(summary = "Perform year-end closing sweep to Retained Earnings")
    public Uni<Response> yearEndClose(YearEndClosingRequest req) {
        return fiscalPeriodService.performYearEndClosing(req.year(), AccountId.of(req.retainedEarningsAccountId()), req.closedBy())
                .map(jeId -> Response.ok(jeId).build());
    }
}
