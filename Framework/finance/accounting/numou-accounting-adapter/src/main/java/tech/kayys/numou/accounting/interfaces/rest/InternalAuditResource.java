package tech.kayys.syirkah.accounting.interfaces.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.syirkah.accounting.application.audit.InternalAuditService;
import tech.kayys.syirkah.accounting.domain.audit.AuditEngagement;
import tech.kayys.syirkah.accounting.domain.audit.AuditFinding;

import java.util.Map;

/**
 * REST adapter for the Internal Audit platform.
 *
 * <p>Endpoints:
 * <pre>
 *   POST   /audit/universe                          — register auditable entity
 *   GET    /audit/universe                          — list all entities
 *   POST   /audit/engagements                       — open engagement
 *   POST   /audit/engagements/{id}/advance          — advance to next phase
 *   GET    /audit/engagements/{id}                  — get engagement
 *   GET    /audit/engagements                       — list all engagements
 *   POST   /audit/engagements/{id}/findings         — record finding
 *   GET    /audit/engagements/{id}/findings         — get findings for engagement
 *   GET    /audit/findings?severity={SEV}           — cross-engagement severity filter
 * </pre>
 */
@ApplicationScoped
@Path("/audit")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InternalAuditResource {

    @Inject
    InternalAuditService auditService;

    // ── Audit Universe ────────────────────────────────────────────────────────

    @POST
    @Path("/universe")
    public Response registerEntity(RegisterEntityRequest req) {
        var entity = auditService.registerAuditableEntity(
                req.entityId(), req.processType(), req.riskRating());
        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                        "entityId", entity.entityId(),
                        "processType", entity.processType(),
                        "riskRating", entity.riskRating()))
                .build();
    }

    @GET
    @Path("/universe")
    public Response listUniverse() {
        var entities = auditService.getAuditUniverse().stream()
                .map(e -> Map.of(
                        "entityId", e.entityId(),
                        "processType", e.processType(),
                        "riskRating", e.riskRating()))
                .toList();
        return Response.ok(entities).build();
    }

    // ── Engagements ───────────────────────────────────────────────────────────

    @POST
    @Path("/engagements")
    public Response openEngagement(OpenEngagementRequest req) {
        var eng = auditService.openEngagement(
                req.entityId(), req.engagementId(), req.title(), req.auditor());
        return Response.status(Response.Status.CREATED)
                .entity(engagementView(eng)).build();
    }

    @POST
    @Path("/engagements/{id}/advance")
    public Response advanceEngagement(@PathParam("id") String id, AdvancePhaseRequest req) {
        var eng = auditService.advanceEngagement(id, AuditEngagement.Phase.valueOf(req.phase()));
        return Response.ok(engagementView(eng)).build();
    }

    @GET
    @Path("/engagements/{id}")
    public Response getEngagement(@PathParam("id") String id) {
        return Response.ok(engagementView(auditService.getEngagement(id))).build();
    }

    @GET
    @Path("/engagements")
    public Response listEngagements() {
        var list = auditService.listEngagements().stream()
                .map(this::engagementView)
                .toList();
        return Response.ok(list).build();
    }

    // ── Findings ─────────────────────────────────────────────────────────────

    @POST
    @Path("/engagements/{id}/findings")
    public Response recordFinding(@PathParam("id") String engagementId, RecordFindingRequest req) {
        var finding = auditService.recordFinding(
                engagementId,
                req.condition(),
                AuditFinding.Severity.valueOf(req.severity()),
                req.recommendation());
        return Response.status(Response.Status.CREATED)
                .entity(findingView(finding)).build();
    }

    @GET
    @Path("/engagements/{id}/findings")
    public Response getFindings(@PathParam("id") String engagementId) {
        var findings = auditService.getFindings(engagementId).stream()
                .map(this::findingView)
                .toList();
        return Response.ok(findings).build();
    }

    @GET
    @Path("/findings")
    public Response findingsBySeverity(@QueryParam("severity") String severity) {
        var findings = auditService.getFindingsBySeverity(AuditFinding.Severity.valueOf(severity))
                .stream()
                .map(this::findingView)
                .toList();
        return Response.ok(findings).build();
    }

    // ── View Helpers ──────────────────────────────────────────────────────────

    private Map<String, Object> engagementView(AuditEngagement e) {
        return Map.of(
                "engagementId", e.engagementId(),
                "entityId", e.entityId(),
                "title", e.title(),
                "auditor", e.auditor(),
                "phase", e.currentPhase().name());
    }

    private Map<String, Object> findingView(AuditFinding f) {
        return Map.of(
                "engagementId", f.engagementId(),
                "condition", f.condition(),
                "severity", f.severity().name(),
                "recommendation", f.recommendation());
    }

    // ── Request Records ───────────────────────────────────────────────────────

    public record RegisterEntityRequest(String entityId, String processType, String riskRating) {}
    public record OpenEngagementRequest(String entityId, String engagementId,
                                        String title, String auditor) {}
    public record AdvancePhaseRequest(String phase) {}
    public record RecordFindingRequest(String condition, String severity, String recommendation) {}
}
