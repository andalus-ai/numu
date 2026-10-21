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

@Path("/api/subjects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Subject Resource", description = "Operations related to subject management")
public class SubjectResource {

    @Inject
    SubjectService subjectService;

    @GET
    @Operation(summary = "Get all subjects", description = "Returns a list of all subjects")
    @APIResponse(responseCode = "200", description = "List of subjects")
    public Response getAllSubjects(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("name") String name,
            @QueryParam("code") String code) {
        
        List<SubjectDTO> subjects = subjectService.findSubjects(page, size, name, code);
        long total = subjectService.countSubjects(name, code);
        
        return Response.ok(subjects)
                .header("X-Total-Count", total)
                .build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get subject by ID", description = "Returns a subject based on ID")
    @APIResponse(responseCode = "200", description = "Subject found")
    @APIResponse(responseCode = "404", description = "Subject not found")
    public Response getSubjectById(
            @Parameter(description = "Subject ID", required = true) @PathParam("id") Long id) {
        
        SubjectDTO subject = subjectService.findById(id);
        return Response.ok(subject).build();
    }

    @POST
    @Operation(summary = "Create new subject", description = "Creates a new subject")
    @APIResponse(responseCode = "201", description = "Subject created successfully")
    @APIResponse(responseCode = "400", description = "Invalid subject data")
    public Response createSubject(@Valid SubjectDTO subjectDTO) {
        SubjectDTO created = subjectService.createSubject(subjectDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update subject", description = "Updates an existing subject")
    @APIResponse(responseCode = "200", description = "Subject updated successfully")
    @APIResponse(responseCode = "404", description = "Subject not found")
    @APIResponse(responseCode = "400", description = "Invalid subject data")
    public Response updateSubject(
            @Parameter(description = "Subject ID", required = true) @PathParam("id") Long id,
            @Valid SubjectDTO subjectDTO) {
        
        SubjectDTO updated = subjectService.updateSubject(id, subjectDTO);
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete subject", description = "Deletes a subject")
    @APIResponse(responseCode = "204", description = "Subject deleted successfully")
    @APIResponse(responseCode = "404", description = "Subject not found")
    public Response deleteSubject(
            @Parameter(description = "Subject ID", required = true) @PathParam("id") Long id) {
        
        subjectService.deleteSubject(id);
        return Response.noContent().build();
    }
}
