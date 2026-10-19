package tech.kayys.numou.foundation.domain.valueobject;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Percentage represented as a human-readable percentage.
 *
 * 10% is represented by BigDecimal("10"), not BigDecimal("0.10").
 * Useful for tax, discount, commission, margin, marketplace fees.
 */
public record Percentage(
        BigDecimal value
) implements ValueObject {

    public Percentage {
        Objects.requireNonNull(
                value,
                "Percentage cannot be null"
        );

        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Percentage cannot be negative"
            );
        }

        if (value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "Percentage cannot exceed 100"
            );
        }
    }

    public static Percentage zero() {
        return new Percentage(BigDecimal.ZERO);
    }

    public static Percentage of(BigDecimal value) {
        return new Percentage(value);
    }

    public static Percentage of(long value) {
        return new Percentage(
                BigDecimal.valueOf(value)
        );
    }

    public static Percentage of(double value) {
        return new Percentage(
                BigDecimal.valueOf(value)
        );
    }

    /**
     * Converts 10% into 0.10.
     */
    public BigDecimal factor() {
        return value.divide(
                BigDecimal.valueOf(100)
        );
    }

}
