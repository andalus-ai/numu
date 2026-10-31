package tech.kayys.numou.numourest;

import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.numourest.SyirkahDtos.*;

import java.net.URI;

/**
 * Thin HTTP skin over {@link SyirkahApi}. Roles: {@code syirkah-operator} runs the books, {@code syirkah-approver}
 * decides pending approvals, {@code syirkah-auditor} reads. Maker and checker must still be different people; the
 * role split does not replace that rule, it only keeps read-only users away from the write endpoints.
 */
@Path("/api/v1/syirkah")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
public class SyirkahResource {

    public static final String OPERATOR = "syirkah-operator";
    public static final String APPROVER = "syirkah-approver";
    public static final String AUDITOR = "syirkah-auditor";

    @Inject
    SyirkahApi api;

    // ---- reads

    @GET
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public Page list(@QueryParam("status") String status, @QueryParam("cursor") String cursor,
                     @QueryParam("limit") Integer limit) {
        return api.list(status, cursor, limit);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public SyirkahView get(@PathParam("id") String id) {
        return api.get(id);
    }

    @GET
    @Path("/{id}/closes")
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public java.util.List<CloseView> closes(@PathParam("id") String id) {
        return api.closes(id);
    }

    @GET
    @Path("/{id}/closes/{closeId}/report.csv")
    @Produces("text/csv")
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public String closeReport(@PathParam("id") String id, @PathParam("closeId") String closeId) {
        return api.closeReportCsv(id, closeId);
    }

    @GET
    @Path("/{id}/partners/{partnerId}/statement.csv")
    @Produces("text/csv")
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public String statement(@PathParam("id") String id, @PathParam("partnerId") String partnerId) {
        return api.statementCsv(id, partnerId);
    }

    @GET
    @Path("/{id}/reconciliation")
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public ReconciliationView reconciliation(@PathParam("id") String id) {
        return api.reconciliation(id);
    }

    @GET
    @Path("/{id}/integrity")
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public IntegrityView integrity(@PathParam("id") String id) {
        return api.integrity(id);
    }

    @POST
    @Path("/{id}/statements/balance-sheet")
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public BalanceSheetView balanceSheet(@PathParam("id") String id, BalanceSheetRequest request) {
        return api.balanceSheet(id, request);
    }

    @GET
    @Path("/{id}/statements/capital-changes")
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public CapitalChangesView capitalChanges(@PathParam("id") String id,
                                             @QueryParam("from") java.time.LocalDate from,
                                             @QueryParam("to") java.time.LocalDate to) {
        return api.capitalChanges(id, from, to);
    }

    @GET
    @Path("/{id}/closes/{closeId}/income-statement")
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public IncomeStatementView incomeStatement(@PathParam("id") String id, @PathParam("closeId") String closeId,
                                               @QueryParam("name") String name) {
        return api.incomeStatement(id, closeId, name);
    }

    @GET
    @Path("/{id}/lifecycle")
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public java.util.List<LifecycleRecordView> lifecycle(@PathParam("id") String id) {
        return api.lifecycleRecords(id);
    }

    @GET
    @Path("/{id}/staff")
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public java.util.List<StaffView> staff(@PathParam("id") String id) {
        return api.staffContracts(id);
    }

    @POST
    @Path("/{id}/staff")
    @RolesAllowed(OPERATOR)
    public Response engageStaff(@PathParam("id") String id, StaffRequest request) {
        return Response.status(Response.Status.CREATED).entity(api.engageStaff(id, request)).build();
    }

    @POST
    @Path("/{id}/closes/preview")
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public ClosePreview previewClose(@PathParam("id") String id, ClosePeriodRequest request) {
        return api.previewClose(id, request);
    }

    @GET
    @Path("/{id}/closes/{closeId}/trading")
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public TradingDto closeTrading(@PathParam("id") String id, @PathParam("closeId") String closeId) {
        return api.closeTrading(id, closeId);
    }

    @GET
    @Path("/audit")
    @RolesAllowed({APPROVER, AUDITOR})
    public AuditView audit() {
        return api.audit();
    }

    @GET
    @Path("/rules")
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public RulesView rules() {
        return api.rules();
    }

    @GET
    @Path("/rules.md")
    @Produces("text/markdown")
    @RolesAllowed({OPERATOR, APPROVER, AUDITOR})
    public String rulesMarkdown() {
        return api.rulesMarkdown();
    }

    // ---- direct commands

    @POST
    @RolesAllowed(OPERATOR)
    public Response open(OpenRequest request) {
        SyirkahView v = api.open(request);
        return Response.created(URI.create("/api/v1/syirkah/" + v.summary().id())).entity(v).build();
    }

    @POST
    @Path("/{id}/closes")
    @RolesAllowed(OPERATOR)
    public Response closePeriod(@PathParam("id") String id, ClosePeriodRequest request) {
        return Response.status(Response.Status.CREATED).entity(api.closePeriod(id, request)).build();
    }

    @POST
    @Path("/{id}/distributions")
    @RolesAllowed(OPERATOR)
    public SyirkahView distribute(@PathParam("id") String id, PaymentRequest request) {
        return api.distribute(id, request);
    }

    @POST
    @Path("/{id}/settlements")
    @RolesAllowed(OPERATOR)
    public SyirkahView settle(@PathParam("id") String id, PaymentRequest request) {
        return api.settle(id, request);
    }

    // ---- governed commands: these only create a pending approval (202)

    @POST
    @Path("/{id}/revisions")
    @RolesAllowed(OPERATOR)
    public Response propose(@PathParam("id") String id, ReviseRequest request) {
        return accepted(api.proposeRevision(id, request));
    }

    @POST
    @Path("/{id}/corrections")
    @RolesAllowed(OPERATOR)
    public Response propose(@PathParam("id") String id, CorrectCloseRequest request) {
        return accepted(api.proposeCorrection(id, request));
    }

    @POST
    @Path("/{id}/liquidation/start")
    @RolesAllowed(OPERATOR)
    public Response proposeStart(@PathParam("id") String id) {
        return accepted(api.proposeStartLiquidation(id));
    }

    @POST
    @Path("/{id}/liquidation")
    @RolesAllowed(OPERATOR)
    public Response proposeLiquidation(@PathParam("id") String id, LiquidateRequest request) {
        return accepted(api.proposeLiquidation(id, request));
    }

    private static Response accepted(ApprovalView v) {
        return Response.accepted(v).location(URI.create("/api/v1/syirkah/approvals/" + v.id())).build();
    }

    // ---- approvals

    @GET
    @Path("/approvals")
    @RolesAllowed({APPROVER, AUDITOR})
    public java.util.List<ApprovalView> pending() {
        return api.pending();
    }

    @POST
    @Path("/approvals/{approvalId}/approve")
    @RolesAllowed(APPROVER)
    public ApprovalView approve(@PathParam("approvalId") String approvalId) {
        return api.approve(approvalId);
    }

    @POST
    @Path("/approvals/{approvalId}/reject")
    @RolesAllowed(APPROVER)
    public ApprovalView reject(@PathParam("approvalId") String approvalId, RejectRequest request) {
        return api.reject(approvalId, request);
    }
}
