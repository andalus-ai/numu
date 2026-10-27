package tech.kayys.numou.workspace.adapter.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.workspace.application.WorkspaceApi;
import tech.kayys.numou.workspace.application.WorkspaceContentApi;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional HTTP surface for existing workspace content use cases.
 * Identity, tenant isolation and authorization remain owned by the configured application APIs.
 */
@Path("/api/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WorkspaceContentResource {
    private final WorkspaceContentApi content;

    @Inject
    public WorkspaceContentResource(WorkspaceContentApi content) {
        this.content = content;
    }

    @POST
    @Path("/workspaces/{workspaceId}/templates")
    public Response createTemplate(@PathParam("workspaceId") UUID workspaceId,
                                   @Valid CreateTemplateRequest request) {
        var created = content.createTemplate(workspaceId, new WorkspaceContentApi.CreateTemplateRequest(
                request.name(), request.title(), blocks(request.blocks())));
        return Response.created(URI.create("/api/v1/templates/" + created.id())).entity(created).build();
    }

    @GET
    @Path("/workspaces/{workspaceId}/templates")
    public List<WorkspaceContentApi.TemplateView> listTemplates(@PathParam("workspaceId") UUID workspaceId) {
        return content.listTemplates(workspaceId);
    }

    @POST
    @Path("/templates/{templateId}/pages")
    public Response createPageFromTemplate(@PathParam("templateId") UUID templateId,
                                           @Valid CreateFromTemplateRequest request) {
        var created = content.createPageFromTemplate(templateId, new WorkspaceContentApi.CreateFromTemplateRequest(
                request.spaceId(), request.parentPageId(), request.titleOverride()));
        return Response.created(URI.create("/api/v1/pages/" + created.id())).entity(created).build();
    }

    @GET
    @Path("/workspaces/{workspaceId}/search")
    public List<WorkspaceContentApi.SearchHitView> searchPages(
            @PathParam("workspaceId") UUID workspaceId,
            @QueryParam("q") @NotBlank String query,
            @QueryParam("spaceId") UUID spaceId,
            @QueryParam("limit") @DefaultValue("20") @Min(1) @Max(100) int limit) {
        return content.searchPages(workspaceId, new WorkspaceContentApi.SearchRequest(spaceId, query, limit));
    }

    @POST
    @Path("/pages/{sourcePageId}/links")
    public Response createLink(@PathParam("sourcePageId") UUID sourcePageId,
                               @Valid CreateLinkRequest request) {
        var created = content.createLink(sourcePageId,
                new WorkspaceContentApi.CreateLinkRequest(request.targetPageId(), request.label()));
        return Response.created(URI.create("/api/v1/pages/" + sourcePageId + "/links/" + created.id()))
                .entity(created).build();
    }

    @GET
    @Path("/pages/{pageId}/links")
    public List<WorkspaceContentApi.LinkView> outgoingLinks(@PathParam("pageId") UUID pageId) {
        return content.outgoingLinks(pageId);
    }

    @GET
    @Path("/pages/{pageId}/backlinks")
    public List<WorkspaceContentApi.BacklinkView> backlinks(@PathParam("pageId") UUID pageId) {
        return content.backlinks(pageId);
    }

    @DELETE
    @Path("/links/{linkId}")
    public Response deleteLink(@PathParam("linkId") UUID linkId) {
        content.deleteLink(linkId);
        return Response.noContent().build();
    }

    private static List<WorkspaceApi.BlockRequest> blocks(List<BlockRequest> requests) {
        return requests == null ? List.of() : requests.stream().map(BlockRequest::toApplicationRequest).toList();
    }

    public record CreateTemplateRequest(@NotBlank String name, @NotBlank String title,
                                        List<@Valid BlockRequest> blocks) {}
    public record CreateFromTemplateRequest(@NotNull UUID spaceId, UUID parentPageId, String titleOverride) {}
    public record CreateLinkRequest(@NotNull UUID targetPageId, String label) {}
    public record BlockRequest(UUID id, @NotBlank String type, String text, int position,
                               Map<String, String> attributes) {
        WorkspaceApi.BlockRequest toApplicationRequest() {
            return new WorkspaceApi.BlockRequest(id, type, text, position,
                    attributes == null ? Map.of() : attributes);
        }
    }
}
