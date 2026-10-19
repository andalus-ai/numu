package tech.kayys.tax.resource;

import tech.kayys.tax.dto.TaxCalculationRequest;
import tech.kayys.tax.dto.TaxCalculationResult;
import tech.kayys.tax.dto.TaxSummaryReport;
import tech.kayys.tax.entity.TaxCalculation;
import tech.kayys.tax.service.TaxCalculatorService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.List;

@Path("/api/tax")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Tax Calculator", description = "Indonesian Corporate Tax Calculator API")
public class TaxCalculatorResource {
    
    @Inject
    TaxCalculatorService taxCalculatorService;
    
    @POST
    @Path("/calculate")
    @Operation(summary = "Calculate corporate tax", description = "Calculate Indonesian corporate tax based on income and expenses")
    public Response calculateTax(@Valid TaxCalculationRequest request) {
        try {
            TaxCalculationResult result = taxCalculatorService.calculateCorporateTax(request);
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Internal server error"))
                .build();
        }
    }
    
    @GET
    @Path("/history/{npwp}")
    @Operation(summary = "Get tax calculation history", description = "Get tax calculation history for a company")
    public Response getTaxHistory(@PathParam("npwp") String npwp) {
        try {
            List<TaxCalculation> history = taxCalculatorService.getCompanyTaxHistory(npwp);
            return Response.ok(history).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    @PUT
    @Path("/recalculate/{calculationId}")
    @Operation(summary = "Recalculate tax", description = "Recalculate tax for existing calculation")
    public Response recalculateTax(@PathParam("calculationId") Long calculationId,
                                 @Valid TaxCalculationRequest request) {
        try {
            TaxCalculationResult result = taxCalculatorService.recalculateTax(calculationId, request);
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/approve/{calculationId}")
    @Operation(summary = "Approve tax calculation", description = "Approve a completed tax calculation")
    public Response approveTaxCalculation(@PathParam("calculationId") Long calculationId,
                                        @QueryParam("reviewedBy") String reviewedBy) {
        try {
            taxCalculatorService.approveTaxCalculation(calculationId, reviewedBy != null ? reviewedBy : "SYSTEM");
            return Response.ok().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/summary/{npwp}/{taxYear}")
    @Operation(summary = "Get annual tax summary", description = "Get annual tax summary report for a company")
    public Response getTaxSummary(@PathParam("npwp") String npwp, @PathParam("taxYear") Integer taxYear) {
        try {
            TaxSummaryReport summary = taxCalculatorService.generateTaxSummaryReport(npwp, taxYear);
            return Response.ok(summary).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    public static class ErrorResponse {
        public String message;
        
        public ErrorResponse(String message) {
            this.message = message;
        }
    }
}
