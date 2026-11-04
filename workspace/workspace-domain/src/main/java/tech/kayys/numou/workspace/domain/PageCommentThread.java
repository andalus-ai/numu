package tech.kayys.numou.workspace.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Thread state plus its append-only comments, scoped to one tenant-owned page. */
public record PageCommentThread(
        UUID id,
        UUID tenantId,
        UUID pageId,
        UUID anchorBlockId,
        boolean resolved,
        long revision,
        UUID resolvedBy,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt,
        List<PageComment> comments) {

    public PageCommentThread(UUID id, UUID tenantId, UUID pageId, boolean resolved, long revision,
                             UUID resolvedBy, Instant resolvedAt, Instant createdAt, Instant updatedAt,
                             List<PageComment> comments) {
        this(id, tenantId, pageId, null, resolved, revision, resolvedBy, resolvedAt,
                createdAt, updatedAt, comments);
    }

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
        Set<UUID> commentIds = new java.util.HashSet<>();
        for (PageComment comment : comments) {
            if (!tenantId.equals(comment.tenantId()) || !pageId.equals(comment.pageId())
                    || !id.equals(comment.threadId())) {
                throw new IllegalArgumentException("thread comments must share tenant, page, and thread scope");
            }
            if (!commentIds.add(comment.id())) throw new IllegalArgumentException("duplicate comment id");
        }
        for (PageComment comment : comments) {
            if (comment.parentCommentId() != null && !commentIds.contains(comment.parentCommentId())) {
                throw new IllegalArgumentException("reply parent must belong to the same thread");
            }
        }
    }

    public static PageCommentThread open(UUID tenantId, UUID pageId, UUID authorId,
                                          String body, Instant now) {
        return open(tenantId, pageId, null, authorId, body, now);
    }

    public static PageCommentThread open(UUID tenantId, UUID pageId, UUID anchorBlockId,
                                          UUID authorId, String body, Instant now) {
        UUID threadId = UUID.randomUUID();
        PageComment root = PageComment.root(tenantId, pageId, threadId, authorId, body, now);
        return new PageCommentThread(threadId, tenantId, pageId, anchorBlockId, false, 1,
                null, null, now, now, List.of(root));
    }

    public PageCommentThread withComments(List<PageComment> entries) {
        Instant latestActivity = entries == null ? updatedAt : entries.stream()
                .map(PageComment::createdAt).max(Instant::compareTo).orElse(updatedAt);
        Instant activityAt = latestActivity.isAfter(updatedAt) ? latestActivity : updatedAt;
        return new PageCommentThread(id, tenantId, pageId, anchorBlockId, resolved, revision,
                resolvedBy, resolvedAt, createdAt, activityAt, entries);
    }

    public PageCommentThread resolve(UUID actorId, Instant now) {
        Objects.requireNonNull(actorId, "actorId");
        if (resolved) return this;
        return new PageCommentThread(id, tenantId, pageId, anchorBlockId, true, revision + 1,
                actorId, now, createdAt, now, comments);
    }

    public PageCommentThread reopen(Instant now) {
        if (!resolved) return this;
        return new PageCommentThread(id, tenantId, pageId, anchorBlockId, false, revision + 1,
                null, null, createdAt, now, comments);
    }
}
