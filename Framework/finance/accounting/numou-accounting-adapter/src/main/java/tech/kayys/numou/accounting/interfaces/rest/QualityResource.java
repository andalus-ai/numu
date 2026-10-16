package tech.kayys.syirkah.accounting.interfaces.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.syirkah.accounting.application.quality.QualityService;
import tech.kayys.syirkah.accounting.domain.quality.InspectionId;
import tech.kayys.syirkah.accounting.domain.quality.NonConformance;

import java.util.Map;

/**
 * REST adapter for the Quality / CAPA platform.
 *
 * <p>Endpoints:
 * <pre>
 *   POST   /quality/inspections               — create inspection
 *   POST   /quality/inspections/{id}/pass      — pass inspection
 *   POST   /quality/inspections/{id}/fail       — fail inspection
 *   GET    /quality/inspections/{id}            — get inspection
 *   POST   /quality/inspections/{id}/ncr        — record NCR
 *   POST   /quality/capa                        — create CAPA from NCR
 * </pre>
 */
@ApplicationScoped
@Path("/quality")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class QualityResource {

    @Inject
    QualityService qualityService;

    // ── Inspections ───────────────────────────────────────────────────────────

    @POST
    @Path("/inspections")
    public Response createInspection(CreateInspectionRequest req) {
        var insp = qualityService.createInspection(req.lotNumber(), req.itemId());
        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                        "inspectionId", insp.id().value(),
                        "lotNumber", insp.lotNumber(),
                        "itemId", insp.itemId(),
                        "status", insp.status().name()))
                .build();
    }

    @POST
    @Path("/inspections/{id}/pass")
    public Response passInspection(@PathParam("id") String id) {
        qualityService.passInspection(new InspectionId(id));
        return Response.ok(Map.of("inspectionId", id, "status", "PASSED")).build();
    }

    @POST
    @Path("/inspections/{id}/fail")
    public Response failInspection(@PathParam("id") String id) {
        qualityService.failInspection(new InspectionId(id));
        return Response.ok(Map.of("inspectionId", id, "status", "FAILED")).build();
    }

    @GET
    @Path("/inspections/{id}")
    public Response getInspection(@PathParam("id") String id) {
        var insp = qualityService.getInspection(new InspectionId(id));
        return Response.ok(Map.of(
                "inspectionId", insp.id().value(),
                "lotNumber", insp.lotNumber(),
                "itemId", insp.itemId(),
                "status", insp.status().name())).build();
    }

    // ── NCR ───────────────────────────────────────────────────────────────────

    @POST
    @Path("/inspections/{id}/ncr")
    public Response recordNcr(@PathParam("id") String inspectionId, RecordNcrRequest req) {
        var ncr = qualityService.recordNcr(
                new InspectionId(inspectionId),
                req.description(),
                NonConformance.Severity.valueOf(req.severity()));
        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                        "ncrId", ncr.ncrId(),
                        "inspectionId", inspectionId,
                        "description", ncr.description(),
                        "severity", ncr.severity().name()))
                .build();
    }

    // ── CAPA ─────────────────────────────────────────────────────────────────

    @POST
    @Path("/capa")
    public Response createCapa(CreateCapaRequest req) {
        // Retrieve the NCR from the inspection
        var insp = qualityService.getInspection(new InspectionId(req.inspectionId()));
        var ncr = qualityService.getNcr(insp, req.ncrId());
        var capa = qualityService.createCapa(ncr, req.actionDescription(), req.ownerId());
        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                        "capaId", capa.capaId(),
                        "ncrId", req.ncrId(),
                        "actionDescription", capa.actionDescription(),
                        "ownerId", capa.ownerId(),
                        "status", capa.status().name()))
                .build();
    }

    // ── Request Records ───────────────────────────────────────────────────────

    public record CreateInspectionRequest(String lotNumber, String itemId) {}
    public record RecordNcrRequest(String description, String severity) {}
    public record CreateCapaRequest(String inspectionId, String ncrId,
                                    String actionDescription, String ownerId) {}
}
