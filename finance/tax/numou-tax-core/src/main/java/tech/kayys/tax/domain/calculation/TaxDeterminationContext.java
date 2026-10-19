package tech.kayys.tax.domain.calculation;

import tech.kayys.tax.domain.category.TaxTreatment;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Context for resolving applicable tax policy.
 */
public record TaxDeterminationContext(
        String tenantId,
        String jurisdictionId,
        String taxType,
        TaxTreatment preferredTreatment,
        LocalDate taxPointDate
) {
    public TaxDeterminationContext {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(jurisdictionId, "jurisdictionId cannot be null");
        Objects.requireNonNull(taxType, "taxType cannot be null");
        Objects.requireNonNull(taxPointDate, "taxPointDate cannot be null");

        tenantId = tenantId.trim();
        jurisdictionId = jurisdictionId.trim();
        taxType = taxType.trim();
    }
}
