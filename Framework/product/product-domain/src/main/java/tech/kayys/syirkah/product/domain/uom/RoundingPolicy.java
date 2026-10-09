package tech.kayys.syirkah.product.domain.uom;

import java.math.RoundingMode;
import java.util.Objects;

/**
 * Rounding policy specifying scale and rounding mode for unit and quantity conversions.
 */
public record RoundingPolicy(
        int scale,
        RoundingMode mode
) {
    public static final RoundingPolicy DEFAULT = new RoundingPolicy(4, RoundingMode.HALF_UP);
    public static final RoundingPolicy EXACT_INTEGER = new RoundingPolicy(0, RoundingMode.UNNECESSARY);

    public RoundingPolicy {
        if (scale < 0) {
            throw new IllegalArgumentException("Scale cannot be negative: " + scale);
        }
        Objects.requireNonNull(mode, "mode cannot be null");
    }
}
