package tech.kayys.numou.workspace.adapter.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.workspace.application.WorkspaceApi;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP transport for workspace page workflows.
 *
 * The host application must provide a configured WorkspaceApi whose IdentityProvider
 * derives tenant and actor from trusted authentication context. Request DTOs intentionally
 * do not accept tenant or actor identifiers.
 */
@Path("/api/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WorkspaceResource {
    private final WorkspaceApi api;

    @Inject
    public WorkspaceResource(WorkspaceApi api) {
        this.api = api;
    }

    @GET
    @Path("/workspaces")
    public List<WorkspaceApi.WorkspaceView> listWorkspaces() {
        return api.listWorkspaces();
    }

    @GET
    @Path("/workspaces/{workspaceId}/spaces")
    public List<WorkspaceApi.SpaceView> listSpaces(@PathParam("workspaceId") UUID workspaceId) {
        return api.listSpaces(workspaceId);
    }

    @POST
    @Path("/workspaces")
    public Response createWorkspace(@Valid CreateWorkspaceRequest request) {
        var created = api.createWorkspace(new WorkspaceApi.CreateWorkspaceRequest(
                request.name(), request.description()));
        return Response.created(URI.create("/api/v1/workspaces/" + created.id()))
                .entity(created).build();
    }

    @POST
    @Path("/workspaces/{workspaceId}/spaces")
    public Response createSpace(@PathParam("workspaceId") UUID workspaceId,
                                @Valid CreateSpaceRequest request) {
        var created = api.createSpace(workspaceId,
                new WorkspaceApi.CreateSpaceRequest(request.name(), request.slug()));
        return Response.created(URI.create("/api/v1/workspaces/" + workspaceId
                + "/spaces/" + created.id())).entity(created).build();
    }

    @POST
    @Path("/workspaces/{workspaceId}/spaces/{spaceId}/pages")
    public Response createPage(@PathParam("workspaceId") UUID workspaceId,
                               @PathParam("spaceId") UUID spaceId,
                               @Valid CreatePageRequest request) {
        var blocks = request.blocks() == null ? List.<WorkspaceApi.BlockRequest>of()
                : request.blocks().stream().map(BlockRequest::toApplicationRequest).toList();
        var created = api.createPage(workspaceId, spaceId,
                new WorkspaceApi.CreatePageRequest(request.parentPageId(), request.title(), blocks));
        return Response.created(URI.create("/api/v1/pages/" + created.id())).entity(created).build();
    }

    @GET
    @Path("/pages/{pageId}")
    public WorkspaceApi.PageView getPage(@PathParam("pageId") UUID pageId) {
        return api.getPage(pageId);
    }

    @PUT
    @Path("/pages/{pageId}")
    public WorkspaceApi.PageView updatePage(@PathParam("pageId") UUID pageId,
                                            @Valid UpdatePageRequest request) {
        var blocks = request.blocks() == null ? List.<WorkspaceApi.BlockRequest>of()
                : request.blocks().stream().map(BlockRequest::toApplicationRequest).toList();
        return api.updatePage(pageId, new WorkspaceApi.UpdatePageRequest(
                request.expectedRevision(), request.title(), blocks, request.changeSummary()));
    }

    @GET
    @Path("/spaces/{spaceId}/pages")
    public List<WorkspaceApi.PageView> listPages(@PathParam("spaceId") UUID spaceId,
                                                 @QueryParam("parentPageId") UUID parentPageId) {
        return api.listChildren(spaceId, parentPageId);
    }

    @POST
    @Path("/pages/{pageId}/history/restore")
    public WorkspaceApi.PageView restorePageRevision(@PathParam("pageId") UUID pageId,
                                                      @Valid RestorePageRevisionRequest request) {
        return api.restorePageRevision(pageId, new WorkspaceApi.RestorePageRevisionRequest(
                request.sourceRevision(), request.expectedCurrentRevision()));
    }

    @GET
    @Path("/pages/{pageId}/history")
    public List<WorkspaceApi.RevisionView> getPageHistory(@PathParam("pageId") UUID pageId) {
        return api.getPageHistory(pageId);
    }

    public record CreateWorkspaceRequest(@NotBlank String name, String description) {}
    public record CreateSpaceRequest(@NotBlank String name, @NotBlank String slug) {}
    public record CreatePageRequest(UUID parentPageId, @NotBlank String title,
                                    List<@Valid BlockRequest> blocks) {}
    public record UpdatePageRequest(long expectedRevision, @NotBlank String title,
                                    List<@Valid BlockRequest> blocks, String changeSummary) {}
    public record RestorePageRevisionRequest(long sourceRevision, long expectedCurrentRevision) {}
    public record BlockRequest(UUID id, @NotBlank String type, String text, int position,
                               Map<String, String> attributes) {
        WorkspaceApi.BlockRequest toApplicationRequest() {
            return new WorkspaceApi.BlockRequest(id, type, text, position,
                    attributes == null ? Map.of() : attributes);
        }
    }
}
