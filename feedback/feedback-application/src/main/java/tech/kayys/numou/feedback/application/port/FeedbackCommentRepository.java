package tech.kayys.numou.feedback.application.port;

import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.feedback.domain.comment.FeedbackComment;
import tech.kayys.numou.feedback.domain.comment.FeedbackCommentId;
import tech.kayys.numou.feedback.domain.comment.FeedbackCommentVisibility;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped persistence contract for append-only feedback discussion entries. */
public interface FeedbackCommentRepository {
    /** Stores the comment and its outbox event atomically; retries return the original comment ID. */
    FeedbackCommentId save(FeedbackComment comment, String idempotencyKey);

    Optional<FeedbackComment> findById(TenantId tenantId, FeedbackCommentId commentId);

    /** Visibility is mandatory to prevent accidental disclosure of internal notes. */
    List<FeedbackComment> findByFeedback(TenantId tenantId, FeedbackId feedbackId,
                                         FeedbackCommentVisibility visibility, int limit, int offset);
}