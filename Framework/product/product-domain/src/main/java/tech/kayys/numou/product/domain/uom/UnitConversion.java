package tech.kayys.syirkah.product.domain.uom;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Conversion between two units of the same category, e.g.
 * 1 BOX = 12 PCS or 1 KG = 1000 GRAM.
 *
 * Conversion factors only make sense within one category, which is
 * validated here rather than at every consuming call site.
 */
public record UnitConversion(
        UnitOfMeasure from,
        UnitOfMeasure to,
        BigDecimal factor
) {

    public UnitConversion {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
        Objects.requireNonNull(factor, "factor cannot be null");

        if (from.equals(to)) {
            throw new IllegalArgumentException(
                    "Cannot convert a unit to itself"
            );
        }

        if (from.category() != to.category()) {
            throw new IllegalArgumentException(
                    "Cannot convert between different unit "
                            + "categories: " + from.category()
                            + " -> " + to.category()
            );
        }

        if (factor.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Conversion factor must be positive"
            );
        }
    }

    /** Converts an amount expressed in {@code from} into {@code to}. */
    public BigDecimal convert(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount cannot be null");

        return amount.multiply(factor);
    }
}