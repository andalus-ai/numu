package tech.kayys.numou.workspace.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Thread state plus its append-only comments, scoped to one tenant-owned page. */
public record PageCommentThread(
        UUID id,
        UUID tenantId,
        UUID pageId,
        boolean resolved,
        long revision,
        UUID resolvedBy,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt,
        List<PageComment> comments) {

    public PageCommentThread {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(pageId, "pageId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        if (resolved != (resolvedAt != null) || resolved != (resolvedBy != null)) {
            throw new IllegalArgumentException("resolved state requires both resolver and resolution time");
        }
        comments = comments == null ? List.of() : List.copyOf(comments);
        if (comments.isEmpty()) throw new IllegalArgumentException("thread must contain its root comment");
        long roots = comments.stream().filter(comment -> comment.parentCommentId() == null).count();
        if (roots != 1) throw new IllegalArgumentException("thread must contain exactly one root comment");
        if (comments.stream().anyMatch(comment -> !tenantId.equals(comment.tenantId())
                || !pageId.equals(comment.pageId()) || !id.equals(comment.threadId()))) {
            throw new IllegalArgumentException("thread comments must share tenant, page, and thread scope");
        }
    }

    public static PageCommentThread open(UUID tenantId, UUID pageId, Instant now) {
        return new PageCommentThread(UUID.randomUUID(), tenantId, pageId, false, 1,
                null, null, now, now, List.of());
    }

    public PageCommentThread withComments(List<PageComment> entries) {
        return new PageCommentThread(id, tenantId, pageId, resolved, revision,
                resolvedBy, resolvedAt, createdAt, updatedAt, entries);
    }

    public PageCommentThread resolve(UUID actorId, Instant now) {
        Objects.requireNonNull(actorId, "actorId");
        if (resolved) return this;
        return new PageCommentThread(id, tenantId, pageId, true, revision + 1,
                actorId, now, createdAt, now, comments);
    }

    public PageCommentThread reopen(Instant now) {
        if (!resolved) return this;
        return new PageCommentThread(id, tenantId, pageId, false, revision + 1,
                null, null, createdAt, now, comments);
    }
}
