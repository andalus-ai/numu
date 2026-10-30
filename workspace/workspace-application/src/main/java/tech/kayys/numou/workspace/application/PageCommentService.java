package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.application.WorkspacePorts.AccessPolicy;
import tech.kayys.numou.workspace.application.WorkspacePorts.Action;
import tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry;
import tech.kayys.numou.workspace.application.WorkspacePorts.AuditedPageCommentRepository;
import tech.kayys.numou.workspace.application.WorkspacePorts.PageRepository;
import tech.kayys.numou.workspace.application.WorkspacePorts.Resource;
import tech.kayys.numou.workspace.domain.Page;
import tech.kayys.numou.workspace.domain.PageComment;
import tech.kayys.numou.workspace.domain.PageCommentThread;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Authorized, tenant-scoped comment workflows. Comment bodies are immutable after creation. */
public final class PageCommentService {
    private final PageRepository pages;
    private final AuditedPageCommentRepository comments;
    private final AccessPolicy access;
    private final Clock clock;

    public PageCommentService(PageRepository pages, AuditedPageCommentRepository comments,
                              AccessPolicy access, Clock clock) {
        this.pages = Objects.requireNonNull(pages, "pages");
        this.comments = Objects.requireNonNull(comments, "comments");
        this.access = Objects.requireNonNull(access, "access");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PageCommentThread createThread(UUID tenantId, UUID actorId, UUID pageId, String body) {
        identity(tenantId, actorId);
        Page page = requirePage(tenantId, pageId);
        access.require(tenantId, actorId, Resource.PAGE, pageId, Action.UPDATE);
        Instant now = clock.instant();
        PageCommentThread thread = PageCommentThread.open(tenantId, pageId, actorId, body, now);
        AuditEntry audit = audit(tenantId, actorId, "PAGE_COMMENT_THREAD_CREATED",
                thread.id(), thread.revision(), page.title(), now);
        if (!comments.createThread(thread, audit)) {
            throw new IllegalStateException("Comment thread could not be created");
        }
        return thread;
    }

    public PageCommentThread reply(UUID tenantId, UUID actorId, UUID pageId, UUID threadId,
                                   UUID parentCommentId, String body) {
        identity(tenantId, actorId);
        Page page = requirePage(tenantId, pageId);
        access.require(tenantId, actorId, Resource.PAGE, pageId, Action.UPDATE);
        PageCommentThread thread = requireThread(tenantId, pageId, threadId);
        if (thread.resolved()) throw new IllegalArgumentException("Resolved threads cannot receive replies");
        boolean parentExists = thread.comments().stream()
                .anyMatch(comment -> comment.id().equals(parentCommentId));
        if (!parentExists) throw new IllegalArgumentException("Reply parent is not part of this thread");
        Instant now = clock.instant();
        PageComment reply = PageComment.reply(tenantId, pageId, threadId,
                parentCommentId, actorId, body, now);
        AuditEntry audit = new AuditEntry(tenantId, actorId, "PAGE_COMMENT_REPLIED",
                "PAGE_COMMENT", reply.id(), 0, page.title(), now);
        if (!comments.appendComment(tenantId, pageId, threadId, reply, audit)) {
            PageCommentThread latest = requireThread(tenantId, pageId, threadId);
            if (latest.revision() != thread.revision()) {
                throw new WorkspaceService.RevisionConflictException(threadId, thread.revision(), latest.revision());
            }
            if (latest.resolved()) throw new IllegalArgumentException("Resolved threads cannot receive replies");
            throw new IllegalStateException("Comment reply could not be appended");
        }
        List<PageComment> entries = new java.util.ArrayList<>(thread.comments());
        entries.add(reply);
        return thread.withComments(entries);
    }

    public PageCommentThread resolve(UUID tenantId, UUID actorId, UUID pageId, UUID threadId,
                                     long expectedRevision) {
        return changeResolution(tenantId, actorId, pageId, threadId, expectedRevision, true);
    }

    public PageCommentThread reopen(UUID tenantId, UUID actorId, UUID pageId, UUID threadId,
                                     long expectedRevision) {
        return changeResolution(tenantId, actorId, pageId, threadId, expectedRevision, false);
    }

    public List<PageCommentThread> listThreads(UUID tenantId, UUID actorId, UUID pageId) {
        identity(tenantId, actorId);
        requirePage(tenantId, pageId);
        access.require(tenantId, actorId, Resource.PAGE, pageId, Action.READ);
        return comments.findThreads(tenantId, pageId);
    }

    public PageCommentThread getThread(UUID tenantId, UUID actorId, UUID pageId, UUID threadId) {
        identity(tenantId, actorId);
        requirePage(tenantId, pageId);
        access.require(tenantId, actorId, Resource.PAGE, pageId, Action.READ);
        return requireThread(tenantId, pageId, threadId);
    }

    private PageCommentThread changeResolution(UUID tenantId, UUID actorId, UUID pageId,
                                               UUID threadId, long expectedRevision, boolean resolve) {
        identity(tenantId, actorId);
        Page page = requirePage(tenantId, pageId);
        access.require(tenantId, actorId, Resource.PAGE, pageId, Action.UPDATE);
        if (expectedRevision < 1) throw new IllegalArgumentException("expectedRevision must be positive");
        PageCommentThread current = requireThread(tenantId, pageId, threadId);
        if (current.revision() != expectedRevision) {
            throw new WorkspaceService.RevisionConflictException(threadId, expectedRevision, current.revision());
        }
        Instant now = clock.instant();
        PageCommentThread changed = resolve ? current.resolve(actorId, now) : current.reopen(now);
        if (changed == current) return current;
        String action = resolve ? "PAGE_COMMENT_THREAD_RESOLVED" : "PAGE_COMMENT_THREAD_REOPENED";
        AuditEntry event = audit(tenantId, actorId, action, threadId, changed.revision(), page.title(), now);
        if (!comments.updateThreadResolution(changed, expectedRevision, event)) {
            PageCommentThread latest = requireThread(tenantId, pageId, threadId);
            if (latest.revision() != expectedRevision) {
                throw new WorkspaceService.RevisionConflictException(threadId, expectedRevision, latest.revision());
            }
            throw new IllegalStateException("Comment thread resolution could not be saved");
        }
        return changed;
    }

    private Page requirePage(UUID tenantId, UUID pageId) {
        return pages.findPage(tenantId, pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found for tenant"));
    }

    private PageCommentThread requireThread(UUID tenantId, UUID pageId, UUID threadId) {
        return comments.findThread(tenantId, pageId, threadId)
                .orElseThrow(() -> new IllegalArgumentException("Comment thread not found for tenant and page"));
    }

    private static AuditEntry audit(UUID tenantId, UUID actorId, String action,
                                    UUID resourceId, long revision, String summary, Instant now) {
        return new AuditEntry(tenantId, actorId, action, "PAGE_COMMENT_THREAD",
                resourceId, revision, summary, now);
    }

    private static void identity(UUID tenantId, UUID actorId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorId, "actorId");
    }
}
