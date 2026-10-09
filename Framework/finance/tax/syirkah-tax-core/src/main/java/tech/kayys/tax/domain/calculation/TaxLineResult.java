package tech.kayys.tax.domain.calculation;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.tax.domain.category.TaxTreatment;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Calculated tax result for a specific line item.
 */
public record TaxLineResult(
        String lineReference,
        String policyCode,
        int policyVersion,
        TaxTreatment treatment,
        Money taxableBase,
        BigDecimal ratePercent,
        Money unroundedTax,
        Money roundedTax
) {
    public TaxLineResult {
        Objects.requireNonNull(lineReference, "lineReference cannot be null");
        Objects.requireNonNull(policyCode, "policyCode cannot be null");
        Objects.requireNonNull(treatment, "treatment cannot be null");
        Objects.requireNonNull(taxableBase, "taxableBase cannot be null");
        Objects.requireNonNull(ratePercent, "ratePercent cannot be null");
        Objects.requireNonNull(unroundedTax, "unroundedTax cannot be null");
        Objects.requireNonNull(roundedTax, "roundedTax cannot be null");
    }
}
