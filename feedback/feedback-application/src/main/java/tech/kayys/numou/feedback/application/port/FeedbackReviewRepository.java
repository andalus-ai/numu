package tech.kayys.numou.feedback.application.port;

import tech.kayys.numou.feedback.domain.review.FeedbackReview;
import tech.kayys.numou.feedback.domain.review.FeedbackReviewId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped persistence contract for moderated reviews. */
public interface FeedbackReviewRepository {
    void save(FeedbackReview review);
    /** Updates moderation state without permitting cross-tenant mutation. */
    void update(FeedbackReview review);
    Optional<FeedbackReview> findById(TenantId tenantId, FeedbackReviewId id);
    /** Tenant/target-scoped review listing; null status means all moderation states. */
    List<FeedbackReview> findByTarget(TenantId tenantId, tech.kayys.numou.feedback.domain.rating.RatingTarget target,
                                      ReviewStatus status, int limit, int offset);
}