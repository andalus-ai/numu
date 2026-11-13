package tech.kayys.numou.feedback.application.port;

import tech.kayys.numou.feedback.domain.rating.FeedbackRating;
import tech.kayys.numou.feedback.domain.rating.FeedbackRatingId;
import tech.kayys.numou.feedback.domain.rating.RatingMetric;
import tech.kayys.numou.feedback.domain.rating.RatingTarget;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped persistence contract for rating responses. */
public interface FeedbackRatingRepository {
    /** Saves a rating idempotently; duplicate keys return the original rating ID. */
    FeedbackRatingId save(FeedbackRating rating, String idempotencyKey);
    Optional<FeedbackRating> findById(TenantId tenantId, FeedbackRatingId id);
    List<FeedbackRating> findByTarget(TenantId tenantId, RatingTarget target, RatingMetric metric);
}