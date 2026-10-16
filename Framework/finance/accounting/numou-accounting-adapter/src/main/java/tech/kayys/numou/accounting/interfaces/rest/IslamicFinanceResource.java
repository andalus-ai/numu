package tech.kayys.syirkah.accounting.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.syirkah.accounting.application.islamic.IslamicFinanceService;
import tech.kayys.syirkah.accounting.domain.islamic.IslamicContractType;
import tech.kayys.syirkah.accounting.domain.islamic.SukukCertificate;
import tech.kayys.syirkah.accounting.domain.islamic.ZakatCalculation;

import java.math.BigDecimal;
import java.time.LocalDate;

@Path("/api/v1/accounting/islamic")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Islamic Finance API", description = "AAOIFI-compliant Islamic financing, Zakat, and Sukuk")
public class IslamicFinanceResource {

    @Inject
    IslamicFinanceService islamicService;

    
public record AssessZakatRequest(BigDecimal currentAssets, BigDecimal currentLiabilities, BigDecimal goldPricePerGram, boolean isSolarYear) {}
    public record RegisterSukukRequest(String certificateId, String name, IslamicContractType contractType, String underlyingAssetReference, BigDecimal faceValue, BigDecimal profitSharingRatio, LocalDate maturityDate) {}

    @POST
    @Path("/zakat/assess")
    @Operation(summary = "Assess corporate Zakat obligation under AAOIFI FAS 9")
    public Uni<Response> assessZakat(AssessZakatRequest req) {
        return Uni.createFrom().item(() -> {
            ZakatCalculation calc = islamicService.assessCorporateZakat(
                    req.currentAssets(), req.currentLiabilities(), req.goldPricePerGram(), req.isSolarYear()
            );
            return Response.ok(calc).build();
        });
    }

    @POST
    @Path("/sukuk")
    @Operation(summary = "Register an asset-backed Sukuk certificate issuance")
    public Uni<Response> registerSukuk(RegisterSukukRequest req) {
        return Uni.createFrom().item(() -> {
            SukukCertificate sukuk = islamicService.registerSukuk(
                    req.certificateId(), req.name(), req.contractType(), req.underlyingAssetReference(), req.faceValue(), req.profitSharingRatio(), req.maturityDate()
            );
            return Response.status(Response.Status.CREATED).entity(sukuk).build();
        });
    }

    @GET
    @Path("/sukuk/{id}")
    @Operation(summary = "Get Sukuk certificate details")
    public Uni<Response> getSukuk(@PathParam("id") String id) {
        return Uni.createFrom().item(() -> {
            return islamicService.findSukuk(id)
                    .map(s -> Response.ok(s).build())
                    .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
        });
    }
}
