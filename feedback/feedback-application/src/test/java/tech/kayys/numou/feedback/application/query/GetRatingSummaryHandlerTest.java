package tech.kayys.numou.feedback.application.query;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.application.port.FeedbackRatingRepository;
import tech.kayys.numou.feedback.domain.rating.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GetRatingSummaryHandlerTest {
    @Test
    void summarizesOnlyTheRequestedTenantTargetAndMetric() {
        TenantId tenant = TenantId.generate();
        RatingTarget target = new RatingTarget("branch", "branch-1", null);
        List<FeedbackRating> ratings = List.of(
                rating(tenant, target, "5"),
                rating(tenant, target, "4"),
                rating(tenant, target, "2"));
        FeedbackRatingRepository repository = new FeedbackRatingRepository() {
            @Override public FeedbackRatingId save(FeedbackRating rating, String key) { return rating.id(); }
            @Override public Optional<FeedbackRating> findById(TenantId t, FeedbackRatingId id) { return Optional.empty(); }
            @Override public List<FeedbackRating> findByTarget(TenantId t, RatingTarget requested, RatingMetric metric) {
                assertEquals(tenant, t);
                assertEquals(target, requested);
                assertEquals(RatingMetric.CSAT, metric);
                return ratings;
            }
        };
        var summary = new GetRatingSummaryHandler(repository).handle(
                new GetRatingSummaryQuery(tenant, target, RatingMetric.CSAT));
        assertEquals(3, summary.responseCount());
        assertEquals(new BigDecimal("66.67"), summary.favorablePercent());
    }

    private FeedbackRating rating(TenantId tenant, RatingTarget target, String value) {
        return FeedbackRating.submit(FeedbackRatingId.generate(), tenant, target,
                RatingMetric.CSAT, RatingScale.of(1, 5), new BigDecimal(value),
                null, null, Instant.parse("2026-11-11T10:00:00Z"));
    }
}