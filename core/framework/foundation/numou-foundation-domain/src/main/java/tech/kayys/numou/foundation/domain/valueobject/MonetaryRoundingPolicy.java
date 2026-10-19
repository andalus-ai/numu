package tech.kayys.numou.foundation.domain.valueobject;

import java.math.RoundingMode;
import java.util.Objects;

/**
 * Monetary rounding policy specifying scale and rounding mode for financial operations.
 */
public record MonetaryRoundingPolicy(
        int scale,
        RoundingMode mode
) implements ValueObject {

    public static final MonetaryRoundingPolicy STANDARD_TWO_DECIMALS =
            new MonetaryRoundingPolicy(2, RoundingMode.HALF_UP);
    public static final MonetaryRoundingPolicy ZERO_DECIMALS =
            new MonetaryRoundingPolicy(0, RoundingMode.HALF_UP);
    public static final MonetaryRoundingPolicy FOUR_DECIMALS =
            new MonetaryRoundingPolicy(4, RoundingMode.HALF_UP);

    public MonetaryRoundingPolicy {
        if (scale < 0) {
            throw new IllegalArgumentException("Scale cannot be negative: " + scale);
        }
        Objects.requireNonNull(mode, "mode cannot be null");
    }

    public Money applyTo(Money money) {
        Objects.requireNonNull(money, "money cannot be null");
        return new Money(money.amount().setScale(scale, mode), money.currency());
    }
}
