package tech.kayys.numou.education.core;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Minimum scores for predikat A, B and C; anything lower is D. Schools set their own
 * KKM / thresholds, so this is a configurable policy and {@link #DEFAULT} is only a starting point.
 */
public record ScoreBands(BigDecimal minA, BigDecimal minB, BigDecimal minC) {
    public static final ScoreBands DEFAULT =
            new ScoreBands(new BigDecimal("90"), new BigDecimal("80"), new BigDecimal("70"));

    public ScoreBands {
        Objects.requireNonNull(minA);
        Objects.requireNonNull(minB);
        Objects.requireNonNull(minC);
        if (minA.compareTo(minB) <= 0 || minB.compareTo(minC) <= 0) {
            throw new IllegalArgumentException("Thresholds must satisfy A > B > C");
        }
    }
}
