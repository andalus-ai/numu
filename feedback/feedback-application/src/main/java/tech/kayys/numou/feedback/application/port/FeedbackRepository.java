package tech.kayys.numou.feedback.application.port;

import tech.kayys.numou.feedback.domain.Feedback;
import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Optional;

/** Capability-owned persistence port; adapters must enforce tenant scope in SQL. */
public interface FeedbackRepository {
    /** Atomically stores the aggregate, idempotency mapping and pending domain events. */
    FeedbackId save(Feedback feedback, String idempotencyKey);
    Optional<Feedback> findById(TenantId tenantId, FeedbackId feedbackId);
}