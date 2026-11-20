package tech.kayys.numou.feedback.application.query;

import tech.kayys.numou.feedback.application.port.FeedbackRatingRepository;
import tech.kayys.numou.feedback.domain.rating.RatingSummary;

import java.util.Objects;

/** Tenant-scoped rating summary query; the repository constrains target, branch and metric. */
public final class GetRatingSummaryHandler {
    private final FeedbackRatingRepository ratings;

    public GetRatingSummaryHandler(FeedbackRatingRepository ratings) {
        this.ratings = Objects.requireNonNull(ratings, "ratings cannot be null");
    }

    public RatingSummary handle(GetRatingSummaryQuery query) {
        Objects.requireNonNull(query, "query cannot be null");
        Objects.requireNonNull(query.tenantId(), "tenantId cannot be null");
        Objects.requireNonNull(query.target(), "target cannot be null");
        Objects.requireNonNull(query.metric(), "metric cannot be null");
        return RatingSummary.from(query.metric(),
                ratings.findByTarget(query.tenantId(), query.target(), query.metric()));
    }
}