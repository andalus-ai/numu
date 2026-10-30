package tech.kayys.numou.workspace.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable comment entry. Replies are appended as new entries; existing content is never overwritten. */
public record PageComment(
        UUID id,
        UUID tenantId,
        UUID pageId,
        UUID threadId,
        UUID parentCommentId,
        UUID authorId,
        String body,
        Instant createdAt) {

    public static final int MAX_BODY_LENGTH = 10_000;

    public PageComment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(pageId, "pageId");
        Objects.requireNonNull(threadId, "threadId");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (id.equals(threadId)) {
            throw new IllegalArgumentException("comment id must be distinct from thread id");
        }
        if (parentCommentId != null && parentCommentId.equals(id)) {
            throw new IllegalArgumentException("comment cannot reply to itself");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("comment body is required");
        }
        body = body.trim();
        if (body.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("comment body exceeds " + MAX_BODY_LENGTH + " characters");
        }
    }

    public static PageComment root(UUID tenantId, UUID pageId, UUID threadId,
                                   UUID authorId, String body, Instant now) {
        return new PageComment(UUID.randomUUID(), tenantId, pageId, threadId,
                null, authorId, body, now);
    }

    public static PageComment reply(UUID tenantId, UUID pageId, UUID threadId,
                                    UUID parentCommentId, UUID authorId, String body, Instant now) {
        return new PageComment(UUID.randomUUID(), tenantId, pageId, threadId,
                Objects.requireNonNull(parentCommentId, "parentCommentId"), authorId, body, now);
    }
}
