package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * Calculates earned value for a single WBS node from approved, scope-matched evidence.
 * The adapter/caller supplies planned and actual completion percentages (0..100) based
 * on the approved baseline and governed progress snapshots for the same report date.
 */
public final class EarnedValueCalculator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int AMOUNT_SCALE = 2;
    private static final int INDEX_SCALE = 4;

    private EarnedValueCalculator() {
    }

    public static EarnedValueMetrics calculate(
            BudgetedCostEvidence budget,
            CumulativeActualCostEvidence actual,
            java.time.LocalDate reportDate,
            BigDecimal plannedPercent,
            BigDecimal actualPercent) {
        Objects.requireNonNull(budget, "Budget evidence cannot be null");
        Objects.requireNonNull(actual, "Actual-cost evidence cannot be null");
        Objects.requireNonNull(reportDate, "Report date cannot be null");
        validatePercent(plannedPercent, "Planned percent");
        validatePercent(actualPercent, "Actual percent");

        if (!budget.isApproved()) {
            throw new IllegalArgumentException("Budget evidence must be approved");
        }
        if (!actual.isApproved()) {
            throw new IllegalArgumentException("Actual-cost evidence must be approved");
        }
        if (!budget.tenantId().equals(actual.tenantId())
                || !budget.structureId().equals(actual.structureId())
                || !budget.nodeId().equals(actual.nodeId())) {
            throw new IllegalArgumentException("Budget and actual-cost evidence must have identical tenant, structure, and WBS node scope");
        }
        if (!budget.currencyCode().equals(actual.currencyCode())) {
            throw new IllegalArgumentException("Budget and actual-cost evidence must use the same currency");
        }
        if (!reportDate.equals(actual.asOfDate())) {
            throw new IllegalArgumentException("Actual-cost evidence date must equal report date");
        }

        BigDecimal bac = money(budget.budgetAtCompletion());
        BigDecimal pv = percentOf(bac, plannedPercent);
        BigDecimal ev = percentOf(bac, actualPercent);
        BigDecimal ac = money(actual.cumulativeActualCost());
        BigDecimal cv = money(ev.subtract(ac));
        BigDecimal sv = money(ev.subtract(pv));
        Optional<BigDecimal> cpi = ac.signum() == 0
                ? Optional.empty() : Optional.of(ev.divide(ac, INDEX_SCALE, RoundingMode.HALF_UP));
        Optional<BigDecimal> spi = pv.signum() == 0
                ? Optional.empty() : Optional.of(ev.divide(pv, INDEX_SCALE, RoundingMode.HALF_UP));

        return new EarnedValueMetrics(
                budget.tenantId(), budget.structureId(), budget.nodeId(), budget.baselineId(),
                reportDate, budget.currencyCode(), bac, pv, ev, ac, cv, sv, cpi, spi);
    }

    private static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return money(amount.multiply(percent).divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private static void validatePercent(BigDecimal percent, String field) {
        Objects.requireNonNull(percent, field + " cannot be null");
        if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException(field + " must be between 0 and 100");
        }
    }
}
