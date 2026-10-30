package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.domain.PageComment;
import tech.kayys.numou.workspace.domain.PageCommentThread;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionDirectory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Transport-neutral comment API. Tenant and actor are derived only from trusted host identity. */
public final class WorkspaceCommentApi {
    private final WorkspaceApi.IdentityProvider identity;
    private final PageCommentService service;
    private final WorkspaceMentionService mentions;

    public WorkspaceCommentApi(WorkspaceApi.IdentityProvider identity, PageCommentService service) {
        this(identity, service, null);
    }

    public WorkspaceCommentApi(WorkspaceApi.IdentityProvider identity, PageCommentService service,
                               WorkspaceMentionService mentions) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.service = Objects.requireNonNull(service, "service");
        this.mentions = mentions;
    }

    public ThreadView createThread(UUID pageId, CreateThreadRequest request) {
        Objects.requireNonNull(pageId, "pageId");
        Objects.requireNonNull(request, "request");
        var actor = currentActor();
        ThreadView created = call(() -> {
            if (mentions != null) CommentMentionParser.parse(request.body());
            return ThreadView.from(service.createThread(actor.tenantId(), actor.actorId(), pageId, request.anchorBlockId(), request.body()));
        });
        if (mentions != null) {
            CommentView root = created.comments().get(0);
            mentions.notifyCommentMentions(actor.tenantId(), actor.actorId(), pageId, root.id(), root.body());
        }
        return created;
    }

    public ThreadView reply(UUID pageId, UUID threadId, ReplyRequest request) {
        Objects.requireNonNull(pageId, "pageId");
        Objects.requireNonNull(threadId, "threadId");
        Objects.requireNonNull(request, "request");
        var actor = currentActor();
        ThreadView updated = call(() -> {
            if (mentions != null) CommentMentionParser.parse(request.body());
            return ThreadView.from(service.reply(actor.tenantId(), actor.actorId(),
                    pageId, threadId, request.parentCommentId(), request.body()));
        });
        if (mentions != null) {
            CommentView reply = updated.comments().get(updated.comments().size() - 1);
            mentions.notifyCommentMentions(actor.tenantId(), actor.actorId(), pageId, reply.id(), reply.body());
        }
        return updated;
    }

    public List<WorkspaceMentionDirectory.Member> suggestMentions(UUID pageId, String query, int limit) {
        Objects.requireNonNull(pageId, "pageId");
        var actor = currentActor();
        if (mentions == null) return List.of();
        return call(() -> mentions.suggest(actor.tenantId(), actor.actorId(), pageId, query, limit));
    }

    public List<ThreadView> listThreads(UUID pageId) {
        Objects.requireNonNull(pageId, "pageId");
        var actor = currentActor();
        return call(() -> service.listThreads(actor.tenantId(), actor.actorId(), pageId)
                .stream().map(ThreadView::from).toList());
    }

    public ThreadView resolve(UUID pageId, UUID threadId, ResolutionRequest request) {
        return resolution(pageId, threadId, request, true);
    }

    public ThreadView reopen(UUID pageId, UUID threadId, ResolutionRequest request) {
        return resolution(pageId, threadId, request, false);
    }

    private ThreadView resolution(UUID pageId, UUID threadId, ResolutionRequest request, boolean resolve) {
        Objects.requireNonNull(pageId, "pageId");
        Objects.requireNonNull(threadId, "threadId");
        Objects.requireNonNull(request, "request");
        var actor = currentActor();
        return call(() -> ThreadView.from(resolve
                ? service.resolve(actor.tenantId(), actor.actorId(), pageId, threadId, request.expectedRevision())
                : service.reopen(actor.tenantId(), actor.actorId(), pageId, threadId, request.expectedRevision())));
    }

    private WorkspaceApi.AuthenticatedActor currentActor() {
        WorkspaceApi.AuthenticatedActor actor = identity.currentActor();
        if (actor == null || actor.tenantId() == null || actor.actorId() == null) {
            throw new WorkspaceApi.ApiException(401, "An authenticated tenant and actor are required");
        }
        return actor;
    }

    private static <T> T call(Operation<T> operation) {
        try {
            return operation.run();
        } catch (WorkspaceService.RevisionConflictException conflict) {
            throw new WorkspaceApi.ApiException(409, conflict.getMessage(), conflict);
        } catch (SecurityException denied) {
            throw new WorkspaceApi.ApiException(403, "Access denied", denied);
        } catch (IllegalArgumentException invalid) {
            throw new WorkspaceApi.ApiException(400, invalid.getMessage(), invalid);
        }
    }

    @FunctionalInterface
    private interface Operation<T> { T run(); }

    public record CreateThreadRequest(String body, UUID anchorBlockId) {
        public CreateThreadRequest(String body) { this(body, null); }
    }
    public record ReplyRequest(UUID parentCommentId, String body) {}
    public record ResolutionRequest(long expectedRevision) {}

    public record CommentView(UUID id, UUID parentCommentId, UUID authorId,
                              String body, Instant createdAt) {
        static CommentView from(PageComment comment) {
            return new CommentView(comment.id(), comment.parentCommentId(), comment.authorId(),
                    comment.body(), comment.createdAt());
        }
    }

    public record ThreadView(UUID id, UUID pageId, UUID anchorBlockId, boolean resolved, long revision,
                             UUID resolvedBy, Instant resolvedAt, Instant createdAt,
                             Instant updatedAt, List<CommentView> comments) {
        public ThreadView { comments = List.copyOf(comments); }
        static ThreadView from(PageCommentThread thread) {
            return new ThreadView(thread.id(), thread.pageId(), thread.anchorBlockId(), thread.resolved(), thread.revision(),
                    thread.resolvedBy(), thread.resolvedAt(), thread.createdAt(), thread.updatedAt(),
                    thread.comments().stream().map(CommentView::from).toList());
        }
    }
}
