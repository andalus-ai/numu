package tech.kayys.tax.domain.policy;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Exact percentage rate for tax calculations, e.g. 11.00 for 11%.
 */
public record TaxRate(BigDecimal percentageValue) {

    public static final TaxRate ZERO = new TaxRate(BigDecimal.ZERO);

    public TaxRate {
        Objects.requireNonNull(percentageValue, "percentageValue cannot be null");
        if (percentageValue.signum() < 0) {
            throw new IllegalArgumentException("Tax rate cannot be negative: " + percentageValue);
        }
    }

    public static TaxRate of(BigDecimal percentage) {
        return new TaxRate(percentage);
    }

    public static TaxRate of(double percentage) {
        return new TaxRate(BigDecimal.valueOf(percentage));
    }

    public static TaxRate of(long percentage) {
        return new TaxRate(BigDecimal.valueOf(percentage));
    }

    /**
     * Applies the rate to a taxable base: base * (rate / 100).
     */
    public BigDecimal applyTo(BigDecimal taxableBase) {
        Objects.requireNonNull(taxableBase, "taxableBase cannot be null");
        if (taxableBase.signum() < 0) {
            throw new IllegalArgumentException("Taxable base cannot be negative: " + taxableBase);
        }
        return taxableBase.multiply(percentageValue).divide(new BigDecimal("100"));
    }
}
