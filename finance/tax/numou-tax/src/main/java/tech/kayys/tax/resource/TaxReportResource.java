package tech.kayys.tax.resource;

import tech.kayys.tax.dto.TaxReportRequest;
import tech.kayys.tax.dto.TaxReportResult;
import tech.kayys.tax.entity.TaxReport;
import tech.kayys.tax.service.TaxReportingService;
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

@Path("/api/tax/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Tax Reports", description = "Tax Reporting and Filing API")
public class TaxReportResource {
    
    @Inject
    TaxReportingService taxReportingService;
    
    @POST
    @Path("/spt-tahunan")
    @Operation(summary = "Generate SPT Tahunan", description = "Generate annual SPT tax return report")
    public Response generateSptTahunan(@Valid TaxReportRequest request) {
        try {
            TaxReportResult result = taxReportingService.generateSptTahunan(request);
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
    @Path("/{reportId}/submit")
    @Operation(summary = "Submit tax report", description = "Submit a tax return with filing acknowledgment number")
    public Response submitReport(@PathParam("reportId") Long reportId,
                               @QueryParam("acknowledgmentNumber") String acknowledgmentNumber) {
        try {
            taxReportingService.submitReport(reportId, acknowledgmentNumber);
            return Response.ok().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new TaxCalculatorResource.ErrorResponse(e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/history/{npwp}")
    @Operation(summary = "Get filing history", description = "Get filed reports history for a company")
    public Response getReports(@PathParam("npwp") String npwp) {
        try {
            List<TaxReport> reports = taxReportingService.getCompanyReports(npwp);
            return Response.ok(reports).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new TaxCalculatorResource.ErrorResponse(e.getMessage()))
                .build();
        }
    }
}
