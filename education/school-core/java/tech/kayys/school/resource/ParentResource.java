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

@Path("/api/parents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Parent Resource", description = "Operations related to parent management")
public class ParentResource {

    @Inject
    ParentService parentService;

    @GET
    @Operation(summary = "Get all parents", description = "Returns a list of all parents")
    @APIResponse(responseCode = "200", description = "List of parents")
    public Response getAllParents(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("name") String name,
            @QueryParam("phone") String phone) {
        
        List<ParentDTO> parents = parentService.findParents(page, size, name, phone);
        long total = parentService.countParents(name, phone);
        
        return Response.ok(parents)
                .header("X-Total-Count", total)
                .build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get parent by ID", description = "Returns a parent based on ID")
    @APIResponse(responseCode = "200", description = "Parent found")
    @APIResponse(responseCode = "404", description = "Parent not found")
    public Response getParentById(
            @Parameter(description = "Parent ID", required = true) @PathParam("id") Long id) {
        
        ParentDTO parent = parentService.findById(id);
        return Response.ok(parent).build();
    }

    @GET
    @Path("/{id}/children")
    @Operation(summary = "Get children of parent", description = "Returns all children of a specific parent")
    @APIResponse(responseCode = "200", description = "List of children")
    @APIResponse(responseCode = "404", description = "Parent not found")
    public Response getChildrenOfParent(
            @Parameter(description = "Parent ID", required = true) @PathParam("id") Long id) {
        
        List<StudentDTO> children = parentService.findChildrenByParentId(id);
        return Response.ok(children).build();
    }

    @POST
    @Operation(summary = "Create new parent", description = "Creates a new parent record")
    @APIResponse(responseCode = "201", description = "Parent created successfully")
    @APIResponse(responseCode = "400", description = "Invalid parent data")
    public Response createParent(@Valid ParentDTO parentDTO) {
        ParentDTO created = parentService.createParent(parentDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update parent", description = "Updates an existing parent record")
    @APIResponse(responseCode = "200", description = "Parent updated successfully")
    @APIResponse(responseCode = "404", description = "Parent not found")
    @APIResponse(responseCode = "400", description = "Invalid parent data")
    public Response updateParent(
            @Parameter(description = "Parent ID", required = true) @PathParam("id") Long id,
            @Valid ParentDTO parentDTO) {
        
        ParentDTO updated = parentService.updateParent(id, parentDTO);
        return Response.ok(updated).build();
    }

    @POST
    @Path("/{parentId}/children/{studentId}")
    @Operation(summary = "Associate child with parent", description = "Associates a student as child of a parent")
    @APIResponse(responseCode = "200", description = "Child associated successfully")
    @APIResponse(responseCode = "404", description = "Parent or student not found")
    public Response associateChildWithParent(
            @Parameter(description = "Parent ID", required = true) @PathParam("parentId") Long parentId,
            @Parameter(description = "Student ID", required = true) @PathParam("studentId") Long studentId) {
        
        parentService.associateChildWithParent(parentId, studentId);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{parentId}/children/{studentId}")
    @Operation(summary = "Remove child association", description = "Removes association between parent and child")
    @APIResponse(responseCode = "204", description = "Association removed successfully")
    @APIResponse(responseCode = "404", description = "Parent or student not found")
    public Response removeChildAssociation(
            @Parameter(description = "Parent ID", required = true) @PathParam("parentId") Long parentId,
            @Parameter(description = "Student ID", required = true) @PathParam("studentId") Long studentId) {
        
        parentService.removeChildAssociation(parentId, studentId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete parent", description = "Deletes a parent record")
    @APIResponse(responseCode = "204", description = "Parent deleted successfully")
    @APIResponse(responseCode = "404", description = "Parent not found")
    public Response deleteParent(
            @Parameter(description = "Parent ID", required = true) @PathParam("id") Long id) {
        
        parentService.deleteParent(id);
        return Response.noContent().build();
    }
}
