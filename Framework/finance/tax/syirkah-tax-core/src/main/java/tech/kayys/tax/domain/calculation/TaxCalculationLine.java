package tech.kayys.tax.domain.calculation;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.Objects;

/**
 * Single line item input for tax calculation.
 */
public record TaxCalculationLine(
        String lineReference,
        Money amount,
        TaxDeterminationContext context
) {
    public TaxCalculationLine {
        Objects.requireNonNull(lineReference, "lineReference cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
    }
}
