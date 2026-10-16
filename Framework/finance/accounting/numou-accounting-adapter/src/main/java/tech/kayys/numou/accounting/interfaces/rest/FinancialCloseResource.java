package tech.kayys.syirkah.accounting.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.syirkah.accounting.application.close.FinancialCloseService;
import tech.kayys.syirkah.accounting.domain.close.CloseCycle;
import tech.kayys.syirkah.accounting.domain.close.CloseCycleId;

import java.util.Map;

@Path("/api/v1/accounting/close")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Financial Close API", description = "Period-end financial close orchestration and period locking")
public class FinancialCloseResource {

    @Inject
    FinancialCloseService closeService;

    
public record OpenCycleRequest(String cycleId, String tenantId, String ledgerId, String fiscalPeriodId) {}
    public record CompleteTaskRequest(String user) {}
    public record ApproveCloseRequest(String approver) {}

    @POST
    @Path("/open")
    @Operation(summary = "Open a new financial close cycle")
    public Uni<Response> openCycle(OpenCycleRequest req) {
        return Uni.createFrom().item(() -> {
            CloseCycleId id = req.cycleId() != null ? CloseCycleId.of(req.cycleId()) : CloseCycleId.generate();
            CloseCycle cycle = closeService.openCycle(id, req.tenantId(), req.ledgerId(), req.fiscalPeriodId());
            return Response.status(Response.Status.CREATED).entity(cycle).build();
        });
    }

    @POST
    @Path("/cycles/{id}/tasks/{taskCode}/complete")
    @Operation(summary = "Mark a close task completed")
    public Uni<Response> completeTask(@PathParam("id") String id, @PathParam("taskCode") String taskCode, CompleteTaskRequest req) {
        return Uni.createFrom().item(() -> {
            closeService.completeTask(CloseCycleId.of(id), taskCode, req.user());
            return Response.ok(Map.of("status", "COMPLETED", "taskCode", taskCode)).build();
        });
    }

    @POST
    @Path("/cycles/{id}/validate")
    @Operation(summary = "Begin validation phase for close cycle")
    public Uni<Response> validate(@PathParam("id") String id) {
        return Uni.createFrom().item(() -> {
            var result = closeService.beginValidation(CloseCycleId.of(id));
            return Response.ok(result).build();
        });
    }

    @POST
    @Path("/cycles/{id}/approve-and-lock")
    @Operation(summary = "Approve close cycle and hard lock accounting period")
    public Uni<Response> approveAndLock(@PathParam("id") String id, ApproveCloseRequest req) {
        return Uni.createFrom().item(() -> {
            CloseCycle cycle = closeService.approveAndLock(CloseCycleId.of(id), req.approver());
            return Response.ok(cycle).build();
        });
    }

    @GET
    @Path("/cycles/{id}")
    @Operation(summary = "Get financial close cycle details")
    public Uni<Response> getById(@PathParam("id") String id) {
        return Uni.createFrom().item(() -> {
            return closeService.findById(CloseCycleId.of(id))
                    .map(c -> Response.ok(c).build())
                    .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
        });
    }
}
