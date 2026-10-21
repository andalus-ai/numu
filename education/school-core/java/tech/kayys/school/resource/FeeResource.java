package tech.kayys.school.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

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

import java.util.List;

@Path("/api/fees")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Fee Resource", description = "Operations related to school fees and payments")
public class FeeResource {

    @Inject
    FeeService feeService;

    @GET
    @Operation(summary = "Get all fees", description = "Returns a list of all fee types")
    @APIResponse(responseCode = "200", description = "List of fees")
    public Response getAllFees(
            @QueryParam("academicYear") String academicYear) {
        
        List<FeeDTO> fees = feeService.findFees(academicYear);
        return Response.ok(fees).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get fee by ID", description = "Returns a fee based on ID")
    @APIResponse(responseCode = "200", description = "Fee found")
    @APIResponse(responseCode = "404", description = "Fee not found")
    public Response getFeeById(
            @Parameter(description = "Fee ID", required = true) @PathParam("id") Long id) {
        
        FeeDTO fee = feeService.findById(id);
        return Response.ok(fee).build();
    }

    @POST
    @Operation(summary = "Create new fee", description = "Creates a new fee type")
    @APIResponse(responseCode = "201", description = "Fee created successfully")
    @APIResponse(responseCode = "400", description = "Invalid fee data")
    public Response createFee(@Valid FeeDTO feeDTO) {
        FeeDTO created = feeService.createFee(feeDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update fee", description = "Updates an existing fee type")
    @APIResponse(responseCode = "200", description = "Fee updated successfully")
    @APIResponse(responseCode = "404", description = "Fee not found")
    @APIResponse(responseCode = "400", description = "Invalid fee data")
    public Response updateFee(
            @Parameter(description = "Fee ID", required = true) @PathParam("id") Long id,
            @Valid FeeDTO feeDTO) {
        
        FeeDTO updated = feeService.updateFee(id, feeDTO);
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete fee", description = "Deletes a fee type")
    @APIResponse(responseCode = "204", description = "Fee deleted successfully")
    @APIResponse(responseCode = "404", description = "Fee not found")
    public Response deleteFee(
            @Parameter(description = "Fee ID", required = true) @PathParam("id") Long id) {
        
        feeService.deleteFee(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/student/{studentId}/invoices")
    @Operation(summary = "Get student invoices", description = "Returns invoices for a specific student")
    @APIResponse(responseCode = "200", description = "List of invoices")
    @APIResponse(responseCode = "404", description = "Student not found")
    public Response getStudentInvoices(
            @Parameter(description = "Student ID", required = true) @PathParam("studentId") Long studentId,
            @QueryParam("academicYear") String academicYear,
            @QueryParam("status") String status) {
        
        Object invoices = feeService.getStudentInvoices(studentId, academicYear, status);
        return Response.ok(invoices).build();
    }

    @POST
    @Path("/generate-invoices/class/{classId}")
    @Operation(summary = "Generate invoices for class", description = "Generates fee invoices for all students in a class")
    @APIResponse(responseCode = "200", description = "Invoices generated successfully")
    @APIResponse(responseCode = "404", description = "Class not found")
    public Response generateInvoicesForClass(
            @Parameter(description = "Class ID", required = true) @PathParam("classId") Long classId,
            @QueryParam("feeId") Long feeId,
            @QueryParam("dueDate") String dueDate) {
        
        Object invoices = feeService.generateInvoicesForClass(classId, feeId, dueDate);
        return Response.ok(invoices).build();
    }

    @POST
    @Path("/payments")
    @Operation(summary = "Record payment", description = "Records a payment for fees")
    @APIResponse(responseCode = "201", description = "Payment recorded successfully")
    @APIResponse(responseCode = "400", description = "Invalid payment data")
    @APIResponse(responseCode = "404", description = "Invoice not found")
    public Response recordPayment(@Valid PaymentDTO paymentDTO) {
        PaymentDTO recorded = feeService.recordPayment(paymentDTO);
        return Response.status(Response.Status.CREATED).entity(recorded).build();
    }

    @GET
    @Path("/payments")
    @Operation(summary = "Get payments", description = "Returns a list of payments")
    @APIResponse(responseCode = "200", description = "List of payments")
    public Response getPayments(
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("studentId") Long studentId) {
        
        List<PaymentDTO> payments = feeService.findPayments(startDate, endDate, studentId);
        return Response.ok(payments).build();
    }

    @GET
    @Path("/reports/outstanding")
    @Operation(summary = "Get outstanding fees report", description = "Returns report of outstanding fees")
    @APIResponse(responseCode = "200", description = "Outstanding fees report")
    public Response getOutstandingFeesReport(
            @QueryParam("classId") Long classId,
            @QueryParam("academicYear") String academicYear) {
        
        Object report = feeService.getOutstandingFeesReport(classId, academicYear);
        return Response.ok(report).build();
    }
}
