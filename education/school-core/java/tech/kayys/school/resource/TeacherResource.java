package tech.kayys.school.resource;



import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.school.service.TeacherService;



@Path("/api/teachers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Teacher Resource", description = "Operations related to teacher management")
public class TeacherResource {

    @Inject
    TeacherService teacherService;

    @GET
    @Operation(summary = "Get all teachers", description = "Returns a list of all teachers")
    @APIResponse(responseCode = "200", description = "List of teachers")
    public Response getAllTeachers(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("nip") String nip,
            @QueryParam("name") String name,
            @QueryParam("subject") String subject) {
        
        List<TeacherDTO> teachers = teacherService.findTeachers(page, size, nip, name, subject);
        long total = teacherService.countTeachers(nip, name, subject);
        
        return Response.ok(teachers)
                .header("X-Total-Count", total)
                .build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get teacher by ID", description = "Returns a teacher based on ID")
    @APIResponse(responseCode = "200", description = "Teacher found")
    @APIResponse(responseCode = "404", description = "Teacher not found")
    public Response getTeacherById(
            @Parameter(description = "Teacher ID", required = true) @PathParam("id") Long id) {
        
        TeacherDTO teacher = teacherService.findById(id);
        return Response.ok(teacher).build();
    }

    @POST
    @Operation(summary = "Create new teacher", description = "Creates a new teacher record")
    @APIResponse(responseCode = "201", description = "Teacher created successfully")
    @APIResponse(responseCode = "400", description = "Invalid teacher data")
    public Response createTeacher(@Valid TeacherDTO teacherDTO) {
        TeacherDTO created = teacherService.createTeacher(teacherDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update teacher", description = "Updates an existing teacher record")
    @APIResponse(responseCode = "200", description = "Teacher updated successfully")
    @APIResponse(responseCode = "404", description = "Teacher not found")
    @APIResponse(responseCode = "400", description = "Invalid teacher data")
    public Response updateTeacher(
            @Parameter(description = "Teacher ID", required = true) @PathParam("id") Long id,
            @Valid TeacherDTO teacherDTO) {
        
        TeacherDTO updated = teacherService.updateTeacher(id, teacherDTO);
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete teacher", description = "Deletes a teacher record")
    @APIResponse(responseCode = "204", description = "Teacher deleted successfully")
    @APIResponse(responseCode = "404", description = "Teacher not found")
    public Response deleteTeacher(
            @Parameter(description = "Teacher ID", required = true) @PathParam("id") Long id) {
        
        teacherService.deleteTeacher(id);
        return Response.noContent().build();
    }
}
