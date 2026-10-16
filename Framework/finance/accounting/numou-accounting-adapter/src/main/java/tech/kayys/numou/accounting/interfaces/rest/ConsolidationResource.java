package tech.kayys.syirkah.accounting.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.syirkah.accounting.consolidation.ConsolidationGroup;
import tech.kayys.syirkah.accounting.consolidation.ConsolidationRun;
import tech.kayys.syirkah.accounting.consolidation.ConsolidationService;
import tech.kayys.syirkah.accounting.consolidation.OwnershipEngine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/accounting/consolidation")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Consolidation API", description = "Multi-entity consolidation and intercompany eliminations")
public class ConsolidationResource {

    @Inject
    ConsolidationService consolidationService;

    @Inject
    OwnershipEngine ownershipEngine;

    
public record DefineGroupRequest(String groupId, String groupCode, String name, String reportingCurrency, String scope) {}
    public record StartRunRequest(String tenantId, LocalDate periodEnd, String presentationCurrency) {}
    public record EffectiveOwnershipRequest(BigDecimal[] holdingChainPercentages) {}

    @POST
    @Path("/groups")
    @Operation(summary = "Define a new consolidation group")
    public Uni<Response> defineGroup(DefineGroupRequest req) {
        return Uni.createFrom().item(() -> {
            ConsolidationGroup group = new ConsolidationGroup(req.groupId(), req.groupCode(), req.name(), req.reportingCurrency(), req.scope());
            return Response.status(Response.Status.CREATED).entity(group).build();
        });
    }

    @POST
    @Path("/runs")
    @Operation(summary = "Start a consolidation run")
    public Uni<Response> startRun(StartRunRequest req) {
        return Uni.createFrom().item(() -> {
            ConsolidationRun run = new ConsolidationRun(UUID.randomUUID(), req.tenantId(), req.periodEnd(), req.presentationCurrency());
            return Response.status(Response.Status.CREATED).entity(run).build();
        });
    }

    @POST
    @Path("/ownership/effective")
    @Operation(summary = "Calculate effective ownership across holding chain")
    public Uni<Response> calculateEffectiveOwnership(EffectiveOwnershipRequest req) {
        return Uni.createFrom().item(() -> {
            BigDecimal effective = ownershipEngine.calculateEffectiveOwnership(req.holdingChainPercentages());
            BigDecimal nci = ownershipEngine.calculateNciPercentage(effective);
            return Response.ok(Map.of("effectiveOwnership", effective, "nciPercentage", nci)).build();
        });
    }
}
