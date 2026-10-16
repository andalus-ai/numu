package tech.kayys.numou.crm.interfaces.rest;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.crm.application.api.CrmService;
import tech.kayys.numou.crm.application.api.command.GenerateConversionReportCommand;
import tech.kayys.numou.crm.application.api.query.ConversionReportView;
import tech.kayys.numou.crm.application.api.query.CrmDashboardView;
import tech.kayys.numou.crm.domain.identifier.ReportId;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletionStage;

/**
 * REST API for CRM reporting and analytics.
 */
@Path("/api/v1/crm/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "CRM Reports", description = "CRM reporting and analytics endpoints")
public class CrmReportResource {

    @Inject
    CrmService crmService;

    @GET
    @Path("/dashboard")
    @Operation(summary = "Get CRM dashboard metrics")
    @APIResponse(responseCode = "200", description = "Dashboard metrics")
    public CompletionStage<Response> getDashboard(
            @QueryParam("period") @DefaultValue("MONTHLY") String period) {
        return crmService.getDashboardMetrics(period)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    @POST
    @Path("/conversion")
    @Operation(summary = "Generate conversion report")
    @APIResponse(responseCode = "200", description = "Conversion report generated")
    @APIResponse(responseCode = "400", description = "Invalid input")
    public CompletionStage<Response> generateConversionReport(
            @QueryParam("period") @DefaultValue("MONTHLY") String period) {
        Instant end = Instant.now();
        Instant start = switch (period.toUpperCase()) {
            case "DAILY" -> end.minus(1, ChronoUnit.DAYS);
            case "WEEKLY" -> end.minus(7, ChronoUnit.DAYS);
            case "MONTHLY" -> end.minus(30, ChronoUnit.DAYS);
            case "QUARTERLY" -> end.minus(90, ChronoUnit.DAYS);
            case "YEARLY" -> end.minus(365, ChronoUnit.DAYS);
            default -> end.minus(30, ChronoUnit.DAYS);
        };

        GenerateConversionReportCommand command = GenerateConversionReportCommand.builder()
            .period(period.toUpperCase())
            .periodStart(start)
            .periodEnd(end)
            .generatedBy("System")
            .build();

        return crmService.generateConversionReport(command)
            .thenApply(reportId -> Response
                .ok(new GenerateReportResponse(reportId))
                .build()
            )
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @GET
    @Path("/conversion/{id}")
    @Operation(summary = "Get conversion report by ID")
    @APIResponse(responseCode = "200", description = "Report found")
    @APIResponse(responseCode = "404", description = "Report not found")
    public CompletionStage<Response> getConversionReport(@PathParam("id") String reportId) {
        return crmService.getConversionReport(reportId)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build)
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @GET
    @Path("/conversion/latest")
    @Operation(summary = "Get latest conversion report")
    @APIResponse(responseCode = "200", description = "Report found")
    @APIResponse(responseCode = "404", description = "No report found")
    public CompletionStage<Response> getLatestConversionReport() {
        return crmService.getLatestConversionReport()
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build)
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @GET
    @Path("/pipeline")
    @Operation(summary = "Get pipeline analytics")
    @APIResponse(responseCode = "200", description = "Pipeline analytics")
    public CompletionStage<Response> getPipelineAnalytics(
            @QueryParam("assignedTo") String assignedTo,
            @QueryParam("customerId") String customerId) {
        java.util.UUID custId = customerId != null && !customerId.isBlank() ? java.util.UUID.fromString(customerId) : null;
        return crmService.getPipelineAnalytics(assignedTo, custId)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    @GET
    @Path("/lead-sources")
    @Operation(summary = "Get lead source analytics")
    @APIResponse(responseCode = "200", description = "Lead source analytics")
    public CompletionStage<Response> getLeadSourceAnalytics(
            @QueryParam("period") @DefaultValue("MONTHLY") String period,
            @QueryParam("fromDate") String fromDate,
            @QueryParam("toDate") String toDate) {
        java.time.Instant from = fromDate != null && !fromDate.isBlank() ? java.time.Instant.parse(fromDate) : null;
        java.time.Instant to = toDate != null && !toDate.isBlank() ? java.time.Instant.parse(toDate) : null;
        return crmService.getLeadSourceAnalytics(period, from, to)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    // =========================================================================
    // Response DTOs
    // =========================================================================

    public static class GenerateReportResponse {
        private final String reportId;

        public GenerateReportResponse(ReportId reportId) {
            this.reportId = reportId.toString();
        }

        public String getReportId() { return reportId; }
    }
}