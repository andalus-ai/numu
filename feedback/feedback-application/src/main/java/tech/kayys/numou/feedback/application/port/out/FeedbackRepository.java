package tech.kayys.numou.feedback.application.port.out;

import tech.kayys.numou.feedback.domain.Feedback;
import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Optional;

/**
 * Persistence port owned by the Feedback capability.
 *
 * Every lookup is explicitly tenant-scoped. Implementations must apply the
 * tenant predicate in the authoritative query, not load globally and filter
 * after retrieval.
 */
public interface FeedbackRepository {

    Feedback save(Feedback feedback);

    Optional<Feedback> findById(TenantId tenantId, FeedbackId feedbackId);
}
