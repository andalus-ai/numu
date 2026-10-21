package tech.kayys.numou.mobility.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * A platform-funded discount. It reduces the rider's price but <b>not the driver's earning</b> (see {@link Settlement}),
 * and never applies to the booking fee, so a promo can never make the fare negative.
 *
 * @param value      percent (0-100) for PERCENT, currency amount for FIXED
 * @param maxDiscount optional cap (null = none)
 */
public record Promo(String code, Type type, BigDecimal value, BigDecimal maxDiscount, BigDecimal minSubtotal, Instant validFrom, Instant validUntil) {

    public enum Type { PERCENT, FIXED }

    public Promo {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        if (value.signum() <= 0 || (type == Type.PERCENT && value.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("Invalid promo value");
        }
        minSubtotal = minSubtotal == null ? BigDecimal.ZERO : minSubtotal;
    }

    boolean applicable(BigDecimal subtotal, Instant now) {
        return !now.isBefore(validFrom) && now.isBefore(validUntil) && subtotal.compareTo(minSubtotal) >= 0;
    }

    BigDecimal discount(BigDecimal subtotal, Instant now, int fractionDigits) {
        if (!applicable(subtotal, now)) {
            return BigDecimal.ZERO.setScale(fractionDigits);
        }
        BigDecimal d = type == Type.PERCENT ? subtotal.multiply(value).divide(BigDecimal.valueOf(100), fractionDigits, RoundingMode.DOWN) : value;
        if (maxDiscount != null && d.compareTo(maxDiscount) > 0) {
            d = maxDiscount;
        }
        if (d.compareTo(subtotal) > 0) {
            d = subtotal;
        }
        return d.setScale(fractionDigits, RoundingMode.DOWN);
    }
}
