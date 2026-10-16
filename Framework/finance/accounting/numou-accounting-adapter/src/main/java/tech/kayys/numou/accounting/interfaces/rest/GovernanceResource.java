package tech.kayys.syirkah.accounting.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.syirkah.accounting.application.hardening.AuditTrailService;
import tech.kayys.syirkah.accounting.application.hardening.IdempotencyGuard;
import tech.kayys.syirkah.accounting.application.hardening.SoDEnforcer;
import tech.kayys.syirkah.accounting.domain.hardening.AuditRecord;

import java.util.List;
import java.util.Map;

@Path("/api/v1/accounting/governance")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Governance & Audit API", description = "Forensic audit trail, maker-checker, and idempotency controls")
public class GovernanceResource {

    @Inject
    AuditTrailService auditTrailService;

    @Inject
    SoDEnforcer sodEnforcer;

    @Inject
    IdempotencyGuard idempotencyGuard;

    
public record SoDCheckRequest(String maker, String checker, String operation) {}
    public record IdempotencyCheckRequest(String idempotencyKey) {}

    @GET
    @Path("/audit")
    @Operation(summary = "Get forensic audit trail entries")
    public Uni<List<AuditRecord>> getAuditTrail() {
        return Uni.createFrom().item(auditTrailService::all);
    }

    @POST
    @Path("/sod/verify")
    @Operation(summary = "Verify Segregation of Duties (Maker != Checker)")
    public Uni<Response> verifySoD(SoDCheckRequest req) {
        return Uni.createFrom().item(() -> {
            try {
                SoDEnforcer.verifySeparateUsers(req.maker(), req.checker(), req.operation() != null ? req.operation() : "Transaction");
                return Response.ok(Map.of("compliant", true, "maker", req.maker(), "checker", req.checker())).build();
            } catch (Exception ex) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("compliant", false, "error", ex.getMessage()))
                        .build();
            }
        });
    }

    @POST
    @Path("/idempotency/verify")
    @Operation(summary = "Verify request idempotency key")
    public Uni<Response> verifyIdempotency(IdempotencyCheckRequest req) {
        return Uni.createFrom().item(() -> {
            boolean acquired = idempotencyGuard.acquire(req.idempotencyKey());
            return Response.ok(Map.of("acquired", acquired, "idempotencyKey", req.idempotencyKey())).build();
        });
    }
}
