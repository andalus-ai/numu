package tech.kayys.numou.construction.domain.boq;

import tech.kayys.numou.quantity.domain.UnitOfMeasure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Explicit BOQ calculation rules. Keep this policy version with each approved
 * revision so historical amounts can be reproduced after policy changes.
 *
 * This class returns a BigDecimal rather than foundation Money because the
 * current BoqRate model uses java.util.Currency. Currency unification should
 * be done as a separate migration to avoid an accidental API break.
 */
public record BoqCalculationPolicy(
        String version,
        int lineAmountScale,
        RoundingMode roundingMode
) {
    public BoqCalculationPolicy {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Policy version cannot be blank");
        }
        if (lineAmountScale < 0) {
            throw new IllegalArgumentException("Line amount scale cannot be negative");
        }
        Objects.requireNonNull(roundingMode, "Rounding mode cannot be null");
    }

    public BigDecimal calculateLineAmount(BoqQuantity quantity, BoqRate rate) {
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(rate, "Rate cannot be null");

        String quantityUnit = quantity.toQuantity().unit().code();
        String rateUnit = new UnitOfMeasure(rate.unit()).code();
        if (!quantityUnit.equals(rateUnit)) {
            throw new IllegalArgumentException(
                    "Quantity unit '" + quantity.unit() + "' does not match rate unit '" + rate.unit() + "'"
            );
        }

        return quantity.value()
                .multiply(rate.amount())
                .setScale(lineAmountScale, roundingMode);
    }

    /** Scalar overload for transaction-bound persistence adapters that reconstruct rows without domain entities. */
    public BigDecimal calculateLineAmount(BigDecimal quantity, String quantityUnit,
                                          BigDecimal rate, String rateUnit) {
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(rate, "Rate cannot be null");
        if (quantity.signum() < 0 || rate.signum() < 0) {
            throw new IllegalArgumentException("Quantity and rate must be non-negative");
        }
        String normalizedQuantityUnit = normalizeUnit(quantityUnit);
        String normalizedRateUnit = normalizeUnit(rateUnit);
        if (!normalizedQuantityUnit.equals(normalizedRateUnit)) {
            throw new IllegalArgumentException("Quantity unit '" + quantityUnit
                    + "' does not match rate unit '" + rateUnit + "'");
        }
        return quantity.multiply(rate).setScale(lineAmountScale, roundingMode);
    }

    private static String normalizeUnit(String unit) {
        return new UnitOfMeasure(unit).code();
    }
}
