package tech.kayys.tax.domain.calculation;

import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.foundation.domain.valueobject.MonetaryRoundingPolicy;

import java.util.List;
import java.util.Objects;

/**
 * Calculation result containing line breakdown and aggregated tax totals.
 */
public record TaxCalculationResult(
        Money totalTaxableBase,
        Money totalTax,
        List<TaxLineResult> lines
) {
    public TaxCalculationResult {
        Objects.requireNonNull(totalTaxableBase, "totalTaxableBase cannot be null");
        Objects.requireNonNull(totalTax, "totalTax cannot be null");
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
