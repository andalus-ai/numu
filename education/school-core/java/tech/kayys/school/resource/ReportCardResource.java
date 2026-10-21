package tech.kayys.school.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/report-cards")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Report Card Resource", description = "Operations related to student report cards")
public class ReportCardResource {

    @Inject
    ReportCardService reportCardService;

    @GET
    @Path("/student/{studentId}")
    @Operation(summary = "Get report cards by student", description = "Returns report cards for a specific student")
    @APIResponse(responseCode = "200", description = "List of report cards")
    @APIResponse(responseCode = "404", description = "Student not found")
    public Response getReportCardsByStudent(
            @Parameter(description = "Student ID", required = true) @PathParam("studentId") Long studentId,
            @QueryParam("academicYear") String academicYear) {
        
        List<ReportCardDTO> reportCards = reportCardService.findReportCardsByStudent(studentId, academicYear);
        return Response.ok(reportCards).build();
    }

    @GET
    @Path("/class/{classId}")
    @Operation(summary = "Get report cards by class", description = "Returns report cards for a specific class")
    @APIResponse(responseCode = "200", description = "List of report cards")
    @APIResponse(responseCode = "404", description = "Class not found")
    public Response getReportCardsByClass(
            @Parameter(description = "Class ID", required = true) @PathParam("classId") Long classId,
            @QueryParam("academicYear") String academicYear,
            @QueryParam("semester") String semester) {
        
        List<ReportCardDTO> reportCards = reportCardService.findReportCardsByClass(classId, academicYear, semester);
        return Response.ok(reportCards).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get report card by ID", description = "Returns a report card based on ID")
    @APIResponse(responseCode = "200", description = "Report card found")
    @APIResponse(responseCode = "404", description = "Report card not found")
    public Response getReportCardById(
            @Parameter(description = "Report Card ID", required = true) @PathParam("id") Long id) {
        
        ReportCardDTO reportCard = reportCardService.findById(id);
        return Response.ok(reportCard).build();
    }

    @POST
    @Path("/generate/student/{studentId}")
    @Operation(summary = "Generate report card", description = "Generates a report card for a specific student")
    @APIResponse(responseCode = "200", description = "Report card generated successfully")
    @APIResponse(responseCode = "404", description = "Student not found")
    public Response generateReportCard(
            @Parameter(description = "Student ID", required = true) @PathParam("studentId") Long studentId,
            @QueryParam("academicYear") String academicYear,
            @QueryParam("semester") String semester) {
        
        ReportCardDTO reportCard = reportCardService.generateReportCard(studentId, academicYear, semester);
        return Response.ok(reportCard).build();
    }

    @POST
    @Path("/generate/class/{classId}")
    @Operation(summary = "Generate report cards for class", description = "Generates report cards for all students in a class")
    @APIResponse(responseCode = "200", description = "Report cards generated successfully")
    @APIResponse(responseCode = "404", description = "Class not found")
    public Response generateReportCardsForClass(
            @Parameter(description = "Class ID", required = true) @PathParam("classId") Long classId,
            @QueryParam("academicYear") String academicYear,
            @QueryParam("semester") String semester) {
        
        List<ReportCardDTO> reportCards = reportCardService.generateReportCardsForClass(classId, academicYear, semester);
        return Response.ok(reportCards).build();
    }

    @GET
    @Path("/{id}/pdf")
    @Produces("application/pdf")
    @Operation(summary = "Get report card PDF", description = "Returns a report card as PDF")
    @APIResponse(responseCode = "200", description = "Report card PDF")
    @APIResponse(responseCode = "404", description = "Report card not found")
    public Response getReportCardPdf(
            @Parameter(description = "Report Card ID", required = true) @PathParam("id") Long id) {
        
        byte[] pdfBytes = reportCardService.generateReportCardPdf(id);
        return Response.ok(pdfBytes)
                .header("Content-Disposition", "attachment; filename=report-card.pdf")
                .build();
    }
}
