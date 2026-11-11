package tech.kayys.numou.feedback.application.port;

import tech.kayys.numou.feedback.domain.review.FeedbackReview;
import tech.kayys.numou.feedback.domain.review.FeedbackReviewId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Optional;

/** Tenant-scoped persistence contract for moderated reviews. */
public interface FeedbackReviewRepository {
    void save(FeedbackReview review);
    Optional<FeedbackReview> findById(TenantId tenantId, FeedbackReviewId id);
}