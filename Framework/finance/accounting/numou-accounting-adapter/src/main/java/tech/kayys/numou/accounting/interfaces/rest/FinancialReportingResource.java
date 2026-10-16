package tech.kayys.syirkah.accounting.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.syirkah.accounting.application.api.query.*;
import tech.kayys.syirkah.accounting.application.cqrs.QueryBus;
import tech.kayys.syirkah.accounting.application.service.FinancialReportingService;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.accounting.domain.report.*;

import java.time.LocalDate;

@Path("/api/v1/accounting/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Financial Reporting API", description = "Endpoints for Balance Sheet, Trial Balance, Income Statement, and Sharia statements")
public class FinancialReportingResource {

    @Inject
    QueryBus queryBus;

    @Inject
    FinancialReportingService reportingService;

    @GET
    @Path("/trial-balance")
    @Operation(summary = "Generate Trial Balance via QueryBus")
    public Uni<TrialBalance> getTrialBalance(
            @HeaderParam("X-Tenant-ID") @DefaultValue("default") String tenantStr,
            @HeaderParam("X-Ledger-ID") @DefaultValue("primary") String ledgerStr,
            @QueryParam("asOfDate") String asOfDateStr
    ) {
        LocalDate date = asOfDateStr != null ? LocalDate.parse(asOfDateStr) : LocalDate.now();
        GetTrialBalanceQuery query = new GetTrialBalanceQuery(TenantRef.of(tenantStr), LedgerId.of(ledgerStr), date);
        return queryBus.execute(query);
    }

    @GET
    @Path("/balance-sheet")
    @Operation(summary = "Generate Balance Sheet via QueryBus")
    public Uni<BalanceSheet> getBalanceSheet(
            @HeaderParam("X-Tenant-ID") @DefaultValue("default") String tenantStr,
            @HeaderParam("X-Ledger-ID") @DefaultValue("primary") String ledgerStr,
            @QueryParam("asOfDate") String asOfDateStr
    ) {
        LocalDate date = asOfDateStr != null ? LocalDate.parse(asOfDateStr) : LocalDate.now();
        GetBalanceSheetQuery query = new GetBalanceSheetQuery(TenantRef.of(tenantStr), LedgerId.of(ledgerStr), date);
        return queryBus.execute(query);
    }

    @GET
    @Path("/income-statement")
    @Operation(summary = "Generate Income Statement via QueryBus")
    public Uni<IncomeStatement> getIncomeStatement(
            @HeaderParam("X-Tenant-ID") @DefaultValue("default") String tenantStr,
            @HeaderParam("X-Ledger-ID") @DefaultValue("primary") String ledgerStr,
            @QueryParam("startDate") String startStr,
            @QueryParam("endDate") String endStr
    ) {
        LocalDate start = startStr != null ? LocalDate.parse(startStr) : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endStr != null ? LocalDate.parse(endStr) : LocalDate.now();
        ReportPeriod period = new ReportPeriod(start, end, "Period " + start + " to " + end);
        GetIncomeStatementQuery query = new GetIncomeStatementQuery(TenantRef.of(tenantStr), LedgerId.of(ledgerStr), period);
        return queryBus.execute(query);
    }

    @GET
    @Path("/cash-flow")
    @Operation(summary = "Generate Cash Flow Statement via QueryBus")
    public Uni<CashFlowStatement> getCashFlow(
            @HeaderParam("X-Tenant-ID") @DefaultValue("default") String tenantStr,
            @HeaderParam("X-Ledger-ID") @DefaultValue("primary") String ledgerStr,
            @QueryParam("startDate") String startStr,
            @QueryParam("endDate") String endStr
    ) {
        LocalDate start = startStr != null ? LocalDate.parse(startStr) : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endStr != null ? LocalDate.parse(endStr) : LocalDate.now();
        ReportPeriod period = new ReportPeriod(start, end, "Period " + start + " to " + end);
        GetCashFlowQuery query = new GetCashFlowQuery(TenantRef.of(tenantStr), LedgerId.of(ledgerStr), period);
        return queryBus.execute(query);
    }

    @GET
    @Path("/sharia-funds")
    @Operation(summary = "Generate Sharia Funds Statement (PSAK 101/109 Zakat & Dana Kebajikan)")
    public Uni<ShariaFundsStatement> getShariaFunds(
            @QueryParam("startDate") String startStr,
            @QueryParam("endDate") String endStr
    ) {
        LocalDate start = startStr != null ? LocalDate.parse(startStr) : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endStr != null ? LocalDate.parse(endStr) : LocalDate.now();
        ReportPeriod period = new ReportPeriod(start, end, "Period " + start + " to " + end);
        return reportingService.generateShariaFundsStatement(period);
    }
}
