package tech.kayys.school.resource;

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

@Path("/api/classes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Class Resource", description = "Operations related to class management")
public class ClassResource {

    @Inject
    ClassService classService;

    @GET
    @Operation(summary = "Get all classes", description = "Returns a list of all classes")
    @APIResponse(responseCode = "200", description = "List of classes")
    public Response getAllClasses(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("name") String name,
            @QueryParam("grade") String grade,
            @QueryParam("academicYear") String academicYear) {
        
        List<ClassDTO> classes = classService.findClasses(page, size, name, grade, academicYear);
        long total = classService.countClasses(name, grade, academicYear);
        
        return Response.ok(classes)
                .header("X-Total-Count", total)
                .build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get class by ID", description = "Returns a class based on ID")
    @APIResponse(responseCode = "200", description = "Class found")
    @APIResponse(responseCode = "404", description = "Class not found")
    public Response getClassById(
            @Parameter(description = "Class ID", required = true) @PathParam("id") Long id) {
        
        ClassDTO classDTO = classService.findById(id);
        return Response.ok(classDTO).build();
    }

    @GET
    @Path("/{id}/students")
    @Operation(summary = "Get students in class", description = "Returns all students in a specific class")
    @APIResponse(responseCode = "200", description = "List of students in class")
    @APIResponse(responseCode = "404", description = "Class not found")
    public Response getStudentsInClass(
            @Parameter(description = "Class ID", required = true) @PathParam("id") Long id) {
        
        List<StudentDTO> students = classService.getStudentsInClass(id);
        return Response.ok(students).build();
    }

    @POST
    @Operation(summary = "Create new class", description = "Creates a new class")
    @APIResponse(responseCode = "201", description = "Class created successfully")
    @APIResponse(responseCode = "400", description = "Invalid class data")
    public Response createClass(@Valid ClassDTO classDTO) {
        ClassDTO created = classService.createClass(classDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update class", description = "Updates an existing class")
    @APIResponse(responseCode = "200", description = "Class updated successfully")
    @APIResponse(responseCode = "404", description = "Class not found")
    @APIResponse(responseCode = "400", description = "Invalid class data")
    public Response updateClass(
            @Parameter(description = "Class ID", required = true) @PathParam("id") Long id,
            @Valid ClassDTO classDTO) {
        
        ClassDTO updated = classService.updateClass(id, classDTO);
        return Response.ok(updated).build();
    }

    @POST
    @Path("/{classId}/students/{studentId}")
    @Operation(summary = "Add student to class", description = "Adds a student to a specific class")
    @APIResponse(responseCode = "200", description = "Student added to class successfully")
    @APIResponse(responseCode = "404", description = "Class or student not found")
    public Response addStudentToClass(
            @Parameter(description = "Class ID", required = true) @PathParam("classId") Long classId,
            @Parameter(description = "Student ID", required = true) @PathParam("studentId") Long studentId) {
        
        classService.addStudentToClass(classId, studentId);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{classId}/students/{studentId}")
    @Operation(summary = "Remove student from class", description = "Removes a student from a specific class")
    @APIResponse(responseCode = "204", description = "Student removed from class successfully")
    @APIResponse(responseCode = "404", description = "Class or student not found")
    public Response removeStudentFromClass(
            @Parameter(description = "Class ID", required = true) @PathParam("classId") Long classId,
            @Parameter(description = "Student ID", required = true) @PathParam("studentId") Long studentId) {
        
        classService.removeStudentFromClass(classId, studentId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete class", description = "Deletes a class")
    @APIResponse(responseCode = "204", description = "Class deleted successfully")
    @APIResponse(responseCode = "404", description = "Class not found")
    public Response deleteClass(
            @Parameter(description = "Class ID", required = true) @PathParam("id") Long id) {
        
        classService.deleteClass(id);
        return Response.noContent().build();
    }
}
