package tech.kayys.syirkah.asset.domain.fixedasset;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * An immutable posted depreciation period entry.
 */
public record DepreciationEntry(
        DepreciationEntryId id,
        int periodYear,
        int periodMonth,
        BigDecimal depreciationAmount,
        BigDecimal accumulatedAfter,
        BigDecimal carryingValueAfter,
        Instant postedAt
) {
    public DepreciationEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(depreciationAmount, "depreciationAmount must not be null");
        Objects.requireNonNull(accumulatedAfter, "accumulatedAfter must not be null");
        Objects.requireNonNull(carryingValueAfter, "carryingValueAfter must not be null");
        Objects.requireNonNull(postedAt, "postedAt must not be null");
        if (periodMonth < 1 || periodMonth > 12) {
            throw new IllegalArgumentException("periodMonth must be between 1 and 12");
        }
        if (depreciationAmount.signum() < 0) {
            throw new IllegalArgumentException("depreciationAmount must be non-negative");
        }
    }

    public LocalDate periodStart() {
        return LocalDate.of(periodYear, periodMonth, 1);
    }
}
