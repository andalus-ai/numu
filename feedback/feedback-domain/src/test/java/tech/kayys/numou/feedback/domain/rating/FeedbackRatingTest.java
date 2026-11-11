package tech.kayys.numou.feedback.domain.rating;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeedbackRatingTest {
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    @Test
    void acceptsCanonicalNpsCsatAndCesScales() {
        assertDoesNotThrow(() -> rating(RatingMetric.NPS, 0, 10, "10"));
        assertDoesNotThrow(() -> rating(RatingMetric.CSAT, 1, 5, "5"));
        assertDoesNotThrow(() -> rating(RatingMetric.CES, 1, 7, "6"));
    }

    @Test
    void rejectsValuesOutsideScaleAndMetricScaleMismatch() {
        assertThrows(IllegalArgumentException.class, () -> rating(RatingMetric.NPS, 0, 10, "11"));
        assertThrows(IllegalArgumentException.class, () -> rating(RatingMetric.NPS, 1, 5, "4"));
        assertThrows(IllegalArgumentException.class, () -> rating(RatingMetric.CSAT, 1, 5, "4.5"));
    }

    @Test
    void customScaleSupportsDecimalValues() {
        FeedbackRating rating = FeedbackRating.submit(FeedbackRatingId.generate(), TenantId.generate(),
                new RatingTarget("service", "delivery-1", "branch-2"), RatingMetric.CUSTOM,
                new RatingScale(new BigDecimal("0.0"), new BigDecimal("10.0")),
                new BigDecimal("7.5"), null, null, NOW);
        assertEquals(new BigDecimal("7.5"), rating.value());
    }

    @Test
    void npsSummaryUsesPromoterPassiveDetractorBands() {
        List<FeedbackRating> ratings = List.of(
                rating(RatingMetric.NPS, 0, 10, "10"),
                rating(RatingMetric.NPS, 0, 10, "9"),
                rating(RatingMetric.NPS, 0, 10, "8"),
                rating(RatingMetric.NPS, 0, 10, "6"),
                rating(RatingMetric.NPS, 0, 10, "0"));
        RatingSummary summary = RatingSummary.from(RatingMetric.NPS, ratings);
        assertEquals(5, summary.responseCount());
        assertEquals(2, summary.promoters());
        assertEquals(1, summary.passives());
        assertEquals(2, summary.detractors());
        assertEquals(0, summary.npsScore());
        assertEquals(new BigDecimal("6.60"), summary.average());
    }

    @Test
    void csatAndCesSummariesExposeFavorableTopBoxPercentages() {
        var csat = RatingSummary.from(RatingMetric.CSAT, List.of(
                rating(RatingMetric.CSAT, 1, 5, "5"),
                rating(RatingMetric.CSAT, 1, 5, "4"),
                rating(RatingMetric.CSAT, 1, 5, "3"),
                rating(RatingMetric.CSAT, 1, 5, "2")));
        assertEquals(2, csat.favorableResponses());
        assertEquals(new BigDecimal("50.00"), csat.favorablePercent());

        var ces = RatingSummary.from(RatingMetric.CES, List.of(
                rating(RatingMetric.CES, 1, 7, "7"),
                rating(RatingMetric.CES, 1, 7, "5"),
                rating(RatingMetric.CES, 1, 7, "4")));
        assertEquals(2, ces.favorableResponses());
        assertEquals(new BigDecimal("66.67"), ces.favorablePercent());
    }

    @Test
    void emptySummaryAndMixedMetricsAreHandled() {
        assertEquals(0, RatingSummary.empty(RatingMetric.CSAT).responseCount());
        assertNull(RatingSummary.empty(RatingMetric.NPS).npsScore());
        assertThrows(IllegalArgumentException.class, () -> RatingSummary.from(RatingMetric.CSAT,
                List.of(rating(RatingMetric.NPS, 0, 10, "9"))));
    }

    private FeedbackRating rating(RatingMetric metric, int min, int max, String value) {
        return FeedbackRating.submit(FeedbackRatingId.generate(), TenantId.generate(),
                new RatingTarget("product", "sku-123", null), metric, RatingScale.of(min, max),
                new BigDecimal(value), new FeedbackActor("user", "u-1"), null, NOW);
    }
}