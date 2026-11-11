package tech.kayys.numou.feedback.domain.rating;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Deterministic aggregation over one tenant/target/metric query result. */
public record RatingSummary(
        RatingMetric metric,
        long responseCount,
        BigDecimal average,
        Map<BigDecimal, Long> distribution,
        Integer npsScore,
        long promoters,
        long passives,
        long detractors) {

    public RatingSummary {
        Objects.requireNonNull(metric, "metric cannot be null");
        if (responseCount < 0) throw new IllegalArgumentException("responseCount cannot be negative");
        distribution = Collections.unmodifiableMap(new TreeMap<>(distribution));
    }

    public static RatingSummary empty(RatingMetric metric) {
        return new RatingSummary(metric, 0, BigDecimal.ZERO.setScale(2), Map.of(), null, 0, 0, 0);
    }

    public static RatingSummary from(RatingMetric metric, Iterable<FeedbackRating> ratings) {
        Objects.requireNonNull(metric, "metric cannot be null");
        Objects.requireNonNull(ratings, "ratings cannot be null");
        long count = 0, promoters = 0, passives = 0, detractors = 0;
        BigDecimal total = BigDecimal.ZERO;
        Map<BigDecimal, Long> distribution = new TreeMap<>();
        for (FeedbackRating rating : ratings) {
            if (rating.metric() != metric) throw new IllegalArgumentException("Mixed metrics in rating aggregation");
            BigDecimal value = rating.value().stripTrailingZeros();
            count++;
            total = total.add(value);
            distribution.merge(value, 1L, Long::sum);
            if (metric == RatingMetric.NPS) {
                int score = value.intValueExact();
                if (score >= 9) promoters++;
                else if (score >= 7) passives++;
                else detractors++;
            }
        }
        if (count == 0) return empty(metric);
        BigDecimal average = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        Integer nps = metric == RatingMetric.NPS
                ? BigDecimal.valueOf(promoters - detractors)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).intValueExact()
                : null;
        return new RatingSummary(metric, count, average, distribution, nps, promoters, passives, detractors);
    }
}