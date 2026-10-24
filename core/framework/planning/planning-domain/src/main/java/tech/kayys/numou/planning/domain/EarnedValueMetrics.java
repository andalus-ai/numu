package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Evidence-backed earned-value metrics for one WBS node at one reporting date.
 * CPI and SPI are absent when their denominator is zero, rather than being fabricated.
 */
public record EarnedValueMetrics(
        UUID tenantId,
        UUID structureId,
        UUID nodeId,
        UUID baselineId,
        LocalDate reportDate,
        String currencyCode,
        BigDecimal budgetAtCompletion,
        BigDecimal plannedValue,
        BigDecimal earnedValue,
        BigDecimal actualCost,
        BigDecimal costVariance,
        BigDecimal scheduleVariance,
        Optional<BigDecimal> costPerformanceIndex,
        Optional<BigDecimal> schedulePerformanceIndex) {

    public EarnedValueMetrics {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(structureId);
        Objects.requireNonNull(nodeId);
        Objects.requireNonNull(baselineId);
        Objects.requireNonNull(reportDate);
        Objects.requireNonNull(currencyCode);
        Objects.requireNonNull(budgetAtCompletion);
        Objects.requireNonNull(plannedValue);
        Objects.requireNonNull(earnedValue);
        Objects.requireNonNull(actualCost);
        Objects.requireNonNull(costVariance);
        Objects.requireNonNull(scheduleVariance);
        Objects.requireNonNull(costPerformanceIndex);
        Objects.requireNonNull(schedulePerformanceIndex);
        if (budgetAtCompletion.signum() < 0 || plannedValue.signum() < 0
                || earnedValue.signum() < 0 || actualCost.signum() < 0) {
            throw new IllegalArgumentException("Cost and value amounts cannot be negative");
        }
        if (costVariance.compareTo(earnedValue.subtract(actualCost)) != 0) {
            throw new IllegalArgumentException("Cost variance must equal earned value minus actual cost");
        }
        if (scheduleVariance.compareTo(earnedValue.subtract(plannedValue)) != 0) {
            throw new IllegalArgumentException("Schedule variance must equal earned value minus planned value");
        }
        costPerformanceIndex.ifPresent(value -> {
            if (value.signum() < 0) throw new IllegalArgumentException("CPI cannot be negative");
        });
        schedulePerformanceIndex.ifPresent(value -> {
            if (value.signum() < 0) throw new IllegalArgumentException("SPI cannot be negative");
        });
    }

    public boolean isOverBudget() {
        return costVariance.signum() < 0;
    }

    public boolean isBehindPlan() {
        return scheduleVariance.signum() < 0;
    }
}
