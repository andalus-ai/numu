package tech.kayys.numou.feedback.application.port;

import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.feedback.domain.FeedbackItem;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port owned by Feedback. Implementations must scope every operation by tenant.
 * save must persist the aggregate and its pending events/outbox records atomically.
 * For a repeated idempotency key, it returns the ID of the original submission.
 */
public interface FeedbackRepository {
    FeedbackId save(FeedbackItem feedback, String idempotencyKey);
    Optional<FeedbackItem> findById(UUID tenantId, FeedbackId feedbackId);
}