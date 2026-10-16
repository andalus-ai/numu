package tech.kayys.tax.resource;

import tech.kayys.tax.dto.TaxPaymentRequest;
import tech.kayys.tax.dto.TaxPaymentResult;
import tech.kayys.tax.entity.TaxPayment;
import tech.kayys.tax.service.TaxPaymentService;
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

@Path("/api/tax/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Tax Payments", description = "Tax Payment Processing API")
public class TaxPaymentResource {
    
    @Inject
    TaxPaymentService taxPaymentService;
    
    @POST
    @Operation(summary = "Process tax payment", description = "Process payment for a tax calculation")
    public Response processPayment(@Valid TaxPaymentRequest request) {
        try {
            TaxPaymentResult result = taxPaymentService.processPayment(request);
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new TaxCalculatorResource.ErrorResponse(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new TaxCalculatorResource.ErrorResponse("Internal server error"))
                .build();
        }
    }
    
    @PUT
    @Path("/{paymentId}/confirm")
    @Operation(summary = "Confirm tax payment", description = "Confirm a tax payment with reference confirmation number")
    public Response confirmPayment(@PathParam("paymentId") Long paymentId, 
                                 @QueryParam("confirmationNumber") String confirmationNumber) {
        try {
            taxPaymentService.confirmPayment(paymentId, confirmationNumber);
            return Response.ok().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new TaxCalculatorResource.ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/history/{npwp}")
    @Operation(summary = "Get payment history", description = "Get tax payment history for a company")
    public Response getPaymentHistory(@PathParam("npwp") String npwp) {
        try {
            List<TaxPayment> history = taxPaymentService.getPaymentHistory(npwp);
            return Response.ok(history).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new TaxCalculatorResource.ErrorResponse(e.getMessage()))
                .build();
        }
    }
}
