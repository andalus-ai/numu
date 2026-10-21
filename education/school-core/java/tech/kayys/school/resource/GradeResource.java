package tech.kayys.school.resource;


import id.schoolmgmt.dto.GradeDTO;
import id.schoolmgmt.service.GradeService;
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

@Path("/api/grades")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Grade Resource", description = "Operations related to student grades")
public class GradeResource {

    @Inject
    GradeService gradeService;

    @GET
    @Path("/student/{studentId}")
    @Operation(summary = "Get grades by student ID", description = "Returns all grades for a specific student")
    @APIResponse(responseCode = "200", description = "List of grades")
    @APIResponse(responseCode = "404", description = "Student not found")
    public Response getGradesByStudentId(
            @Parameter(description = "Student ID", required = true) @PathParam("studentId") Long studentId,
            @QueryParam("semester") String semester,
            @QueryParam("academicYear") String academicYear) {
        
        List<GradeDTO> grades = gradeService.findGradesByStudentId(studentId, semester, academicYear);
        return Response.ok(grades).build();
    }

    @GET
    @Path("/subject/{subjectId}/class/{classId}")
    @Operation(summary = "Get grades by subject and class", description = "Returns all grades for a specific subject and class")
    @APIResponse(responseCode = "200", description = "List of grades")
    @APIResponse(responseCode = "404", description = "Subject or class not found")
    public Response getGradesBySubjectAndClass(
            @Parameter(description = "Subject ID", required = true) @PathParam("subjectId") Long subjectId,
            @Parameter(description = "Class ID", required = true) @PathParam("classId") Long classId,
            @QueryParam("semester") String semester,
            @QueryParam("academicYear") String academicYear) {
        
        List<GradeDTO> grades = gradeService.findGradesBySubjectAndClass(subjectId, classId, semester, academicYear);
        return Response.ok(grades).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get grade by ID", description = "Returns a grade based on ID")
    @APIResponse(responseCode = "200", description = "Grade found")
    @APIResponse(responseCode = "404", description = "Grade not found")
    public Response getGradeById(
            @Parameter(description = "Grade ID", required = true) @PathParam("id") Long id) {
        
        GradeDTO grade = gradeService.findById(id);
        return Response.ok(grade).build();
    }

    @POST
    @Operation(summary = "Create new grade", description = "Creates a new grade record")
    @APIResponse(responseCode = "201", description = "Grade created successfully")
    @APIResponse(responseCode = "400", description = "Invalid grade data")
    @APIResponse(responseCode = "404", description = "Student, subject, or teacher not found")
    public Response createGrade(@Valid GradeDTO gradeDTO) {
        GradeDTO created = gradeService.createGrade(gradeDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update grade", description = "Updates an existing grade record")
    @APIResponse(responseCode = "200", description = "Grade updated successfully")
    @APIResponse(responseCode = "404", description = "Grade not found")
    @APIResponse(responseCode = "400", description = "Invalid grade data")
    public Response updateGrade(
            @Parameter(description = "Grade ID", required = true) @PathParam("id") Long id,
            @Valid GradeDTO gradeDTO) {
        
        GradeDTO updated = gradeService.updateGrade(id, gradeDTO);
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete grade", description = "Deletes a grade record")
    @APIResponse(responseCode = "204", description = "Grade deleted successfully")
    @APIResponse(responseCode = "404", description = "Grade not found")
    public Response deleteGrade(
            @Parameter(description = "Grade ID", required = true) @PathParam("id") Long id) {
        
        gradeService.deleteGrade(id);
        return Response.noContent().build();
    }
}
