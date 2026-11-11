package tech.kayys.numou.feedback.domain.rating;

import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Immutable rating response. One record represents one respondent's answer to one metric. */
public record FeedbackRating(
        FeedbackRatingId id,
        TenantId tenantId,
        RatingTarget target,
        RatingMetric metric,
        RatingScale scale,
        BigDecimal value,
        FeedbackActor respondent,
        String surveyReference,
        Instant submittedAt) {

    public FeedbackRating {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(metric, "metric cannot be null");
        Objects.requireNonNull(scale, "scale cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        Objects.requireNonNull(submittedAt, "submittedAt cannot be null");
        if (!scale.contains(value)) throw new IllegalArgumentException("Rating value is outside the configured scale");
        if (metric != RatingMetric.CUSTOM) {
            if (!scale.min().equals(BigDecimal.valueOf(metric.defaultMin()))
                    || !scale.max().equals(BigDecimal.valueOf(metric.defaultMax()))) {
                throw new IllegalArgumentException("Scale does not match metric " + metric);
            }
            try {
                value.intValueExact();
            } catch (ArithmeticException fractionalValue) {
                throw new IllegalArgumentException(metric + " requires an integer score", fractionalValue);
            }
        }
        surveyReference = normalize(surveyReference);
        if (surveyReference != null && surveyReference.length() > 200) {
            throw new IllegalArgumentException("surveyReference exceeds 200 characters");
        }
    }

    public static FeedbackRating submit(FeedbackRatingId id, TenantId tenantId, RatingTarget target,
                                        RatingMetric metric, RatingScale scale, BigDecimal value,
                                        FeedbackActor respondent, String surveyReference, Instant submittedAt) {
        return new FeedbackRating(id, tenantId, target, metric, scale, value,
                respondent, surveyReference, submittedAt);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}