package tech.kayys.syirkah.construction.domain.controls;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record EarnedValueSnapshot(
        LocalDate snapshotDate,
        BigDecimal plannedValue,
        BigDecimal earnedValue,
        BigDecimal actualCost
) {
    public EarnedValueSnapshot {
        Objects.requireNonNull(snapshotDate);
        Objects.requireNonNull(plannedValue);
        Objects.requireNonNull(earnedValue);
        Objects.requireNonNull(actualCost);
    }

    public BigDecimal costVariance() { return earnedValue.subtract(actualCost); }
    public BigDecimal scheduleVariance() { return earnedValue.subtract(plannedValue); }
}
