package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Same-currency roll-up of node-level earned-value metrics for one tenant, baseline and date.
 * Callers must supply a mutually exclusive set of WBS cost scopes (for example, leaves only)
 * so parent and child amounts are not counted twice.
 */
public record EarnedValuePortfolioReport(
        UUID tenantId,
        UUID structureId,
        UUID baselineId,
        LocalDate reportDate,
        String currencyCode,
        List<EarnedValueMetrics> nodes,
        BigDecimal budgetAtCompletion,
        BigDecimal plannedValue,
        BigDecimal earnedValue,
        BigDecimal actualCost,
        BigDecimal costVariance,
        BigDecimal scheduleVariance,
        BigDecimal costPerformanceIndex,
        BigDecimal schedulePerformanceIndex) {

    public EarnedValuePortfolioReport {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(structureId);
        Objects.requireNonNull(baselineId);
        Objects.requireNonNull(reportDate);
        Objects.requireNonNull(currencyCode);
        nodes = List.copyOf(Objects.requireNonNull(nodes));
        Objects.requireNonNull(budgetAtCompletion);
        Objects.requireNonNull(plannedValue);
        Objects.requireNonNull(earnedValue);
        Objects.requireNonNull(actualCost);
        Objects.requireNonNull(costVariance);
        Objects.requireNonNull(scheduleVariance);
        Objects.requireNonNull(costPerformanceIndex);
        Objects.requireNonNull(schedulePerformanceIndex);
        if (nodes.isEmpty()) throw new IllegalArgumentException("At least one node metric is required");
        Set<UUID> nodeIds = new HashSet<>();
        for (EarnedValueMetrics node : nodes) {
            Objects.requireNonNull(node, "Node metrics cannot contain null");
            if (!nodeIds.add(node.nodeId())) throw new IllegalArgumentException("Duplicate WBS node in roll-up");
            if (!tenantId.equals(node.tenantId()) || !structureId.equals(node.structureId())
                    || !baselineId.equals(node.baselineId()) || !reportDate.equals(node.reportDate())
                    || !currencyCode.equals(node.currencyCode())) {
                throw new IllegalArgumentException("All node metrics must match tenant, structure, baseline, date, and currency");
            }
        }
        requireEqual(budgetAtCompletion, sum(nodes, EarnedValueMetrics::budgetAtCompletion), "BAC");
        requireEqual(plannedValue, sum(nodes, EarnedValueMetrics::plannedValue), "PV");
        requireEqual(earnedValue, sum(nodes, EarnedValueMetrics::earnedValue), "EV");
        requireEqual(actualCost, sum(nodes, EarnedValueMetrics::actualCost), "AC");
        requireEqual(costVariance, earnedValue.subtract(actualCost), "CV");
        requireEqual(scheduleVariance, earnedValue.subtract(plannedValue), "SV");
        if (costPerformanceIndex.signum() < 0 || schedulePerformanceIndex.signum() < 0) {
            throw new IllegalArgumentException("Portfolio performance indices cannot be negative");
        }
        if (actualCost.signum() == 0 || plannedValue.signum() == 0) {
            throw new IllegalArgumentException("Portfolio indices are undefined when their denominators are zero");
        }
        requireEqual(costPerformanceIndex, earnedValue.divide(actualCost, 4, RoundingMode.HALF_UP), "CPI");
        requireEqual(schedulePerformanceIndex, earnedValue.divide(plannedValue, 4, RoundingMode.HALF_UP), "SPI");
    }

    public static EarnedValuePortfolioReport fromNodes(List<EarnedValueMetrics> nodeMetrics) {
        List<EarnedValueMetrics> nodes = List.copyOf(Objects.requireNonNull(nodeMetrics));
        if (nodes.isEmpty()) throw new IllegalArgumentException("At least one node metric is required");
        EarnedValueMetrics first = Objects.requireNonNull(nodes.get(0));
        BigDecimal bac = sum(nodes, EarnedValueMetrics::budgetAtCompletion);
        BigDecimal pv = sum(nodes, EarnedValueMetrics::plannedValue);
        BigDecimal ev = sum(nodes, EarnedValueMetrics::earnedValue);
        BigDecimal ac = sum(nodes, EarnedValueMetrics::actualCost);
        if (ac.signum() == 0 || pv.signum() == 0) {
            throw new IllegalArgumentException("Cannot create a portfolio report with zero AC or PV; portfolio CPI/SPI would be undefined");
        }
        return new EarnedValuePortfolioReport(first.tenantId(), first.structureId(), first.baselineId(),
                first.reportDate(), first.currencyCode(), nodes, bac, pv, ev, ac,
                ev.subtract(ac).setScale(2, RoundingMode.HALF_UP),
                ev.subtract(pv).setScale(2, RoundingMode.HALF_UP),
                ev.divide(ac, 4, RoundingMode.HALF_UP),
                ev.divide(pv, 4, RoundingMode.HALF_UP));
    }

    public boolean isOverBudget() { return costVariance.signum() < 0; }
    public boolean isBehindPlan() { return scheduleVariance.signum() < 0; }

    private static BigDecimal sum(List<EarnedValueMetrics> nodes,
            java.util.function.Function<EarnedValueMetrics, BigDecimal> amount) {
        return nodes.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static void requireEqual(BigDecimal actual, BigDecimal expected, String field) {
        if (actual.compareTo(expected) != 0) {
            throw new IllegalArgumentException(field + " must match the supplied node metrics");
        }
    }
}
