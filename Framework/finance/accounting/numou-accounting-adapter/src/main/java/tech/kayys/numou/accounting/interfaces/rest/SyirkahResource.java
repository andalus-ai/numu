package tech.kayys.numou.accounting.interfaces.rest;

import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.accounting.application.islamic.SyirkahApplicationService;
import tech.kayys.numou.accounting.domain.islamic.SyirkahBusinessResult;
import tech.kayys.numou.accounting.domain.islamic.SyirkahCalculation;
import tech.kayys.numou.accounting.domain.islamic.SyirkahContractVersion;
import tech.kayys.numou.accounting.domain.islamic.SyirkahParty;
import tech.kayys.numou.accounting.domain.islamic.SyirkahPartyRole;
import tech.kayys.numou.accounting.domain.islamic.SyirkahType;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Syirkah API. Tenant scope and actor are sourced from the verified request context,
 * never from client-supplied tenant/actor fields.
 */
@Path("/api/v1/accounting/syirkah")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Syirkah Accounting", description = "Partnership contracts, period results, calculations and approvals")
public class SyirkahResource {
    @Inject SyirkahApplicationService service;
    @Inject RequestContextHolder requestContext;

    public record PartyRequest(String partyId, String displayName, SyirkahPartyRole role,
                               BigDecimal capitalContribution, BigDecimal profitNisbahPercent) {}
    public record CreateContractRequest(String companyId, String contractId, int version, String currencyCode,
                                        SyirkahType type, LocalDate effectiveFrom, LocalDate effectiveUntil,
                                        String shariaPolicyVersion, List<PartyRequest> parties) {}
    public record ActivateContractRequest(String signedEvidenceReference) {}
    public record BusinessResultRequest(String companyId, String resultId, String contractId, int contractVersion,
                                        LocalDate periodStart, LocalDate periodEnd, BigDecimal totalRevenue,
                                        BigDecimal directCosts, BigDecimal operatingCosts, BigDecimal otherCosts,
                                        String currencyCode, String evidenceReference) {}
    public record ApprovalRequest(String companyId) {}

    @POST
    @Path("/contracts")
    @Blocking
    @Operation(summary = "Create an immutable Syirkah contract version in DRAFT")
    public Response createContract(CreateContractRequest request) {
        String tenant = requireTenant();
        String actor = requireActor();
        List<SyirkahParty> parties = request.parties().stream().map(p -> new SyirkahParty(
                p.partyId(), p.displayName(), p.role(), p.capitalContribution(), p.profitNisbahPercent())).toList();
        var contract = new SyirkahContractVersion(request.contractId(), request.version(), tenant,
                request.companyId(), request.currencyCode(), request.type(), request.effectiveFrom(),
                request.effectiveUntil(), request.shariaPolicyVersion(), parties);
        service.createContract(contract, tenant, actor);
        return Response.status(Response.Status.CREATED).entity(contract).build();
    }

    @POST
    @Path("/contracts/{id}/versions")
    @Blocking
    @Operation(summary = "Draft an immutable amendment to an active Syirkah contract")
    public Response createContractVersion(@PathParam("id") String id, CreateContractRequest request) {
        if (!id.equals(request.contractId())) {
            throw new jakarta.ws.rs.BadRequestException("Path contract id must match request contract id");
        }
        String tenant = requireTenant();
        String actor = requireActor();
        List<SyirkahParty> parties = request.parties().stream().map(p -> new SyirkahParty(
                p.partyId(), p.displayName(), p.role(), p.capitalContribution(), p.profitNisbahPercent())).toList();
        var contract = new SyirkahContractVersion(request.contractId(), request.version(), tenant,
                request.companyId(), request.currencyCode(), request.type(), request.effectiveFrom(),
                request.effectiveUntil(), request.shariaPolicyVersion(), parties);
        service.createContractVersion(contract, tenant, actor);
        return Response.status(Response.Status.CREATED).entity(contract).build();
    }

    @POST
    @Path("/contracts/{id}/versions/{version}/activate")
    @Blocking
    @Operation(summary = "Activate a contract after independent signed-evidence review")
    public Response activateContract(@PathParam("id") String id, @PathParam("version") int version,
                                     ActivateContractRequest request, @jakarta.ws.rs.QueryParam("companyId") String companyId) {
        service.activateContract(requireTenant(), companyId, id, version, requireActor(), request.signedEvidenceReference());
        return Response.ok(java.util.Map.of("contractId", id, "version", version, "status", "ACTIVE")).build();
    }

    @POST
    @Path("/results")
    @Blocking
    @Operation(summary = "Register a business-period result with evidence reference")
    public Response registerResult(BusinessResultRequest request) {
        var result = new SyirkahBusinessResult(request.resultId(), request.contractId(), request.contractVersion(),
                request.periodStart(), request.periodEnd(), request.totalRevenue(), request.directCosts(),
                request.operatingCosts(), request.otherCosts(), request.currencyCode(), request.evidenceReference());
        service.registerBusinessResult(requireTenant(), request.companyId(), result, requireActor());
        return Response.status(Response.Status.CREATED).entity(result).build();
    }

    @POST
    @Path("/results/{id}/calculate")
    @Blocking
    @Operation(summary = "Calculate and persist an immutable allocation snapshot")
    public Response calculate(@PathParam("id") String id, @jakarta.ws.rs.QueryParam("companyId") String companyId) {
        SyirkahCalculation calculation = service.calculate(requireTenant(), companyId, id, requireActor());
        return Response.status(Response.Status.CREATED).entity(calculation).build();
    }

    @GET
    @Path("/results/{id}/calculations/{version}")
    @Blocking
    @Operation(summary = "Retrieve a persisted calculation snapshot")
    public Response getCalculation(@PathParam("id") String id, @PathParam("version") int version,
                                   @jakarta.ws.rs.QueryParam("companyId") String companyId) {
        return Response.ok(service.findCalculation(requireTenant(), companyId, id, version)).build();
    }

    @POST
    @Path("/results/{id}/calculations/{version}/approve")
    @Blocking
    @Operation(summary = "Approve a calculation using maker-checker separation")
    public Response approve(@PathParam("id") String id, @PathParam("version") int version, ApprovalRequest request) {
        return Response.ok(service.approveCalculation(requireTenant(), request.companyId(), id, version, requireActor())).build();
    }

    // Cash settlement is intentionally not exposed here until a production journal-posting
    // gateway can atomically post and reconcile the distribution obligation.

    private String requireTenant() {
        if (!requestContext.tenantVerified() || requestContext.tenantId() == null || requestContext.tenantId().isBlank()) {
            throw new NotAuthorizedException("Authenticated tenant context is required");
        }
        return requestContext.tenantId();
    }

    private String requireActor() {
        String actor = requestContext.actor();
        if (actor == null || actor.isBlank()) throw new NotAuthorizedException("Authenticated actor context is required");
        return actor;
    }
}
