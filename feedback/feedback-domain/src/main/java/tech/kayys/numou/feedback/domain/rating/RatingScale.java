package tech.kayys.numou.feedback.domain.rating;

import java.math.BigDecimal;
import java.util.Objects;

/** Inclusive numeric rating scale. Supports decimal custom scales without float rounding. */
public record RatingScale(BigDecimal min, BigDecimal max) {
    public RatingScale {
        Objects.requireNonNull(min, "min cannot be null");
        Objects.requireNonNull(max, "max cannot be null");
        if (min.compareTo(max) >= 0) throw new IllegalArgumentException("min must be less than max");
        if (min.scale() > 4 || max.scale() > 4) throw new IllegalArgumentException("scale precision cannot exceed 4 decimal places");
    }

    public static RatingScale of(int min, int max) {
        return new RatingScale(BigDecimal.valueOf(min), BigDecimal.valueOf(max));
    }

    public boolean contains(BigDecimal value) {
        return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }
}