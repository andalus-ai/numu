package tech.kayys.syirkah.accounting.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.syirkah.accounting.application.fund.FundAccountingService;
import tech.kayys.syirkah.accounting.domain.fund.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Path("/api/v1/accounting/funds")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Fund Accounting API", description = "Non-profit and public sector fund & grant accounting")
public class FundAccountingResource {

    @Inject
    FundAccountingService fundService;

    
public record CreateFundRequest(String fundId, String code, String name, FundType type, BigDecimal initialBalance) {}
    public record RegisterGrantRequest(String grantId, String code, String donorName, String linkedFundId, BigDecimal awardedAmount, LocalDate expiryDate) {}
    public record RecordExpenditureRequest(BigDecimal amount) {}
    public record TransferFundsRequest(String sourceFundId, String targetFundId, BigDecimal amount) {}

    @POST
    @Operation(summary = "Create a new self-balancing fund")
    public Uni<Response> createFund(CreateFundRequest req) {
        return Uni.createFrom().item(() -> {
            FundId id = req.fundId() != null ? FundId.of(req.fundId()) : FundId.generate();
            Fund fund = fundService.createFund(id, req.code(), req.name(), req.type(), req.initialBalance());
            return Response.status(Response.Status.CREATED).entity(fund).build();
        });
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get fund by ID")
    public Uni<Response> getFund(@PathParam("id") String id) {
        return Uni.createFrom().item(() -> {
            return fundService.findFund(FundId.of(id))
                    .map(f -> Response.ok(f).build())
                    .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
        });
    }

    @POST
    @Path("/grants")
    @Operation(summary = "Register donor grant")
    public Uni<Response> registerGrant(RegisterGrantRequest req) {
        return Uni.createFrom().item(() -> {
            GrantId id = req.grantId() != null ? GrantId.of(req.grantId()) : GrantId.generate();
            Grant grant = fundService.registerGrant(id, req.code(), req.donorName(), FundId.of(req.linkedFundId()), req.awardedAmount(), req.expiryDate());
            return Response.status(Response.Status.CREATED).entity(grant).build();
        });
    }

    @POST
    @Path("/grants/{id}/expend")
    @Operation(summary = "Record expenditure against donor grant")
    public Uni<Response> recordExpenditure(@PathParam("id") String id, RecordExpenditureRequest req) {
        return Uni.createFrom().item(() -> {
            fundService.recordGrantExpenditure(GrantId.of(id), req.amount());
            return Response.ok(Map.of("status", "RECORDED", "grantId", id, "expendedAmount", req.amount())).build();
        });
    }

    @POST
    @Path("/transfers")
    @Operation(summary = "Transfer money between funds")
    public Uni<Response> transferFunds(TransferFundsRequest req) {
        return Uni.createFrom().item(() -> {
            fundService.transfer(FundId.of(req.sourceFundId()), FundId.of(req.targetFundId()), req.amount());
            return Response.ok(Map.of("status", "TRANSFERRED", "amount", req.amount())).build();
        });
    }
}
