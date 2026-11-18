package tech.kayys.numou.feedback.domain.comment;

import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/**
 * Append-only discussion entry. Editing/deletion is intentionally excluded from
 * M7 so that audit history remains immutable; moderation is a later policy.
 */
public record FeedbackComment(
        FeedbackCommentId id,
        TenantId tenantId,
        FeedbackId feedbackId,
        FeedbackCommentId parentCommentId,
        FeedbackActor author,
        FeedbackCommentVisibility visibility,
        String body,
        Instant createdAt) {

    public FeedbackComment {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(feedbackId, "feedbackId cannot be null");
        Objects.requireNonNull(author, "author cannot be null");
        Objects.requireNonNull(visibility, "visibility cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        if (body == null || body.isBlank()) throw new IllegalArgumentException("body cannot be blank");
        body = body.trim();
        if (body.length() > 20_000) throw new IllegalArgumentException("body exceeds 20000 characters");
    }

    public static FeedbackComment add(FeedbackCommentId id, TenantId tenantId, FeedbackId feedbackId,
                                      FeedbackCommentId parentCommentId, FeedbackActor author,
                                      FeedbackCommentVisibility visibility, String body, Instant createdAt) {
        return new FeedbackComment(id, tenantId, feedbackId, parentCommentId,
                author, visibility, body, createdAt);
    }
}