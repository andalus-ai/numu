package tech.kayys.numou.feedback.application.port;

import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.feedback.domain.FeedbackItem;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port owned by Feedback. Implementations must scope every operation by tenant.
 * save must persist the aggregate and its pending events/outbox records atomically.
 */
public interface FeedbackRepository {
    void save(FeedbackItem feedback, String idempotencyKey);
    Optional<FeedbackItem> findById(UUID tenantId, FeedbackId feedbackId);
}