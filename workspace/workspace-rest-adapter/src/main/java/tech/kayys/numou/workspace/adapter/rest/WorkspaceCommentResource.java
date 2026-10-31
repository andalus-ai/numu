package tech.kayys.numou.workspace.adapter.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.workspace.application.WorkspaceCommentApi;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** HTTP endpoints for page discussion threads; authentication and authorization stay in the application boundary. */
@Path("/api/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WorkspaceCommentResource {
    private final WorkspaceCommentApi comments;

    @Inject
    public WorkspaceCommentResource(WorkspaceCommentApi comments) {
        this.comments = comments;
    }

    @GET
    @Path("/pages/{pageId}/mention-suggestions")
    public List<tech.kayys.numou.workspace.application.port.WorkspaceMentionDirectory.Member> suggestMentions(
            @PathParam("pageId") UUID pageId,
            @QueryParam("q") String query,
            @QueryParam("limit") Integer limit) {
        return comments.suggestMentions(pageId, query, limit == null ? 8 : limit);
    }

    @GET
    @Path("/pages/{pageId}/comment-threads")
    public List<WorkspaceCommentApi.ThreadView> listThreads(@PathParam("pageId") UUID pageId) {
        return comments.listThreads(pageId);
    }

    @POST
    @Path("/pages/{pageId}/comment-threads")
    public Response createThread(@PathParam("pageId") UUID pageId, @Valid CreateThreadRequest request) {
        var created = comments.createThread(pageId, new WorkspaceCommentApi.CreateThreadRequest(request.body(), request.anchorBlockId()));
        return Response.created(URI.create("/api/v1/pages/" + pageId
                + "/comment-threads/" + created.id())).entity(created).build();
    }

    @POST
    @Path("/pages/{pageId}/comment-threads/{threadId}/replies")
    public Response reply(@PathParam("pageId") UUID pageId, @PathParam("threadId") UUID threadId,
                          @Valid ReplyRequest request) {
        var updated = comments.reply(pageId, threadId,
                new WorkspaceCommentApi.ReplyRequest(request.parentCommentId(), request.body()));
        return Response.status(Response.Status.CREATED).entity(updated).build();
    }

    @POST
    @Path("/pages/{pageId}/comment-threads/{threadId}/resolve")
    public WorkspaceCommentApi.ThreadView resolve(@PathParam("pageId") UUID pageId,
                                                   @PathParam("threadId") UUID threadId,
                                                   @Valid ResolutionRequest request) {
        return comments.resolve(pageId, threadId,
                new WorkspaceCommentApi.ResolutionRequest(request.expectedRevision()));
    }

    @POST
    @Path("/pages/{pageId}/comment-threads/{threadId}/reopen")
    public WorkspaceCommentApi.ThreadView reopen(@PathParam("pageId") UUID pageId,
                                                  @PathParam("threadId") UUID threadId,
                                                  @Valid ResolutionRequest request) {
        return comments.reopen(pageId, threadId,
                new WorkspaceCommentApi.ResolutionRequest(request.expectedRevision()));
    }

    public record CreateThreadRequest(@NotBlank String body, UUID anchorBlockId) { public CreateThreadRequest(String body) { this(body, null); } }
    public record ReplyRequest(@NotNull UUID parentCommentId, @NotBlank String body) {}
    public record ResolutionRequest(@Positive long expectedRevision) {}
}
