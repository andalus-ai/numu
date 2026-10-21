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

@Path("/api/exams")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Exam Resource", description = "Operations related to exams and exam results")
public class ExamResource {

    @Inject
    ExamService examService;

    @GET
    @Operation(summary = "Get all exams", description = "Returns a list of all exams")
    @APIResponse(responseCode = "200", description = "List of exams")
    public Response getAllExams(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("subjectId") Long subjectId,
            @QueryParam("academicYear") String academicYear,
            @QueryParam("semester") String semester) {
        
        List<ExamDTO> exams = examService.findExams(page, size, subjectId, academicYear, semester);
        long total = examService.countExams(subjectId, academicYear, semester);
        
        return Response.ok(exams)
                .header("X-Total-Count", total)
                .build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get exam by ID", description = "Returns an exam based on ID")
    @APIResponse(responseCode = "200", description = "Exam found")
    @APIResponse(responseCode = "404", description = "Exam not found")
    public Response getExamById(
            @Parameter(description = "Exam ID", required = true) @PathParam("id") Long id) {
        
        ExamDTO exam = examService.findById(id);
        return Response.ok(exam).build();
    }

    @POST
    @Operation(summary = "Create new exam", description = "Creates a new exam")
    @APIResponse(responseCode = "201", description = "Exam created successfully")
    @APIResponse(responseCode = "400", description = "Invalid exam data")
    public Response createExam(@Valid ExamDTO examDTO) {
        ExamDTO created = examService.createExam(examDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update exam", description = "Updates an existing exam")
    @APIResponse(responseCode = "200", description = "Exam updated successfully")
    @APIResponse(responseCode = "404", description = "Exam not found")
    @APIResponse(responseCode = "400", description = "Invalid exam data")
    public Response updateExam(
            @Parameter(description = "Exam ID", required = true) @PathParam("id") Long id,
            @Valid ExamDTO examDTO) {
        
        ExamDTO updated = examService.updateExam(id, examDTO);
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete exam", description = "Deletes an exam")
    @APIResponse(responseCode = "204", description = "Exam deleted successfully")
    @APIResponse(responseCode = "404", description = "Exam not found")
    public Response deleteExam(
            @Parameter(description = "Exam ID", required = true) @PathParam("id") Long id) {
        
        examService.deleteExam(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/{examId}/results")
    @Operation(summary = "Get exam results", description = "Returns all results for a specific exam")
    @APIResponse(responseCode = "200", description = "List of exam results")
    @APIResponse(responseCode = "404", description = "Exam not found")
    public Response getExamResults(
            @Parameter(description = "Exam ID", required = true) @PathParam("examId") Long examId) {
        
        List<ExamResultDTO> results = examService.findResultsByExamId(examId);
        return Response.ok(results).build();
    }

    @GET
    @Path("/results/student/{studentId}")
    @Operation(summary = "Get exam results by student", description = "Returns all exam results for a specific student")
    @APIResponse(responseCode = "200", description = "List of exam results")
    @APIResponse(responseCode = "404", description = "Student not found")
    public Response getStudentExamResults(
            @Parameter(description = "Student ID", required = true) @PathParam("studentId") Long studentId,
            @QueryParam("academicYear") String academicYear,
            @QueryParam("semester") String semester) {
        
        List<ExamResultDTO> results = examService.findResultsByStudentId(studentId, academicYear, semester);
        return Response.ok(results).build();
    }

    @POST
    @Path("/results")
    @Operation(summary = "Record exam results", description = "Records results for an exam")
    @APIResponse(responseCode = "201", description = "Exam results recorded successfully")
    @APIResponse(responseCode = "400", description = "Invalid exam result data")
    @APIResponse(responseCode = "404", description = "Exam or student not found")
    public Response recordExamResults(@Valid List<ExamResultDTO> resultDTOs) {
        List<ExamResultDTO> recorded = examService.recordExamResults(resultDTOs);
        return Response.status(Response.Status.CREATED).entity(recorded).build();
    }

    @PUT
    @Path("/results/{id}")
    @Operation(summary = "Update exam result", description = "Updates an existing exam result")
    @APIResponse(responseCode = "200", description = "Exam result updated successfully")
    @APIResponse(responseCode = "404", description = "Exam result not found")
    @APIResponse(responseCode = "400", description = "Invalid exam result data")
    public Response updateExamResult(
            @Parameter(description = "Exam Result ID", required = true) @PathParam("id") Long id,
            @Valid ExamResultDTO resultDTO) {
        
        ExamResultDTO updated = examService.updateExamResult(id, resultDTO);
        return Response.ok(updated).build();
    }
}
