package tech.kayys.tax.domain.policy;

import tech.kayys.tax.domain.category.TaxTreatment;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Governed, effective-dated tax policy definition.
 */
public record TaxPolicy(
        String id,
        String code,
        String jurisdictionId,
        String taxType,
        TaxTreatment treatment,
        TaxRate rate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        TaxPolicyStatus status,
        int version
) {
    public TaxPolicy {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(jurisdictionId, "jurisdictionId cannot be null");
        Objects.requireNonNull(taxType, "taxType cannot be null");
        Objects.requireNonNull(treatment, "treatment cannot be null");
        Objects.requireNonNull(rate, "rate cannot be null");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom cannot be null");
        Objects.requireNonNull(status, "status cannot be null");

        id = id.trim();
        code = code.trim().toUpperCase();
        jurisdictionId = jurisdictionId.trim();
        taxType = taxType.trim();

        if (id.isEmpty() || code.isEmpty()) {
            throw new IllegalArgumentException("Tax policy id and code cannot be blank");
        }

        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo cannot be before effectiveFrom");
        }

        if (version < 1) {
            throw new IllegalArgumentException("version must be at least 1");
        }
    }

    public boolean isEffectiveOn(LocalDate date) {
        Objects.requireNonNull(date, "date cannot be null");
        if (date.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !date.isAfter(effectiveTo);
    }
}
