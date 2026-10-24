package tech.kayys.numou.planning.application.usecase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import tech.kayys.numou.planning.application.port.out.BudgetedCostEvidenceSource;
import tech.kayys.numou.planning.application.port.out.CumulativeActualCostEvidenceSource;
import tech.kayys.numou.planning.domain.BudgetedCostEvidence;
import tech.kayys.numou.planning.domain.CumulativeActualCostEvidence;
import tech.kayys.numou.planning.domain.EarnedValueCalculator;
import tech.kayys.numou.planning.domain.EarnedValueMetrics;
import tech.kayys.numou.planning.domain.EarnedValuePortfolioReport;

/**
 * Application use case that assembles approved cost evidence with progress percentages
 * supplied by the caller's governed progress-snapshot query, then calculates earned value.
 *
 * <p>The caller must pass a mutually exclusive set of cost scopes (normally WBS leaves).
 * This use case intentionally does not query a concrete progress or finance implementation.</p>
 */
public final class CalculateEarnedValueReport {
    private final BudgetedCostEvidenceSource budgetSource;
    private final CumulativeActualCostEvidenceSource actualCostSource;

    public CalculateEarnedValueReport(
            BudgetedCostEvidenceSource budgetSource,
            CumulativeActualCostEvidenceSource actualCostSource) {
        this.budgetSource = Objects.requireNonNull(budgetSource, "Budget evidence source cannot be null");
        this.actualCostSource = Objects.requireNonNull(actualCostSource, "Actual-cost evidence source cannot be null");
    }

    public Result execute(Command command) {
        Objects.requireNonNull(command, "Command cannot be null");
        List<UUID> nodeIds = command.progress().stream().map(NodeProgress::nodeId).toList();

        List<BudgetedCostEvidence> budgets = Objects.requireNonNull(
                budgetSource.findApproved(command.tenantId(), command.structureId(), command.baselineId(), nodeIds),
                "Budget evidence source returned null");
        List<CumulativeActualCostEvidence> actualCosts = Objects.requireNonNull(
                actualCostSource.findApproved(command.tenantId(), command.structureId(), command.reportDate(), nodeIds),
                "Actual-cost evidence source returned null");

        Map<UUID, BudgetedCostEvidence> budgetByNode = indexBudgets(budgets, command);
        Map<UUID, CumulativeActualCostEvidence> actualByNode = indexActualCosts(actualCosts, command);
        List<EarnedValueMetrics> metrics = new ArrayList<>(nodeIds.size());

        for (NodeProgress progress : command.progress()) {
            BudgetedCostEvidence budget = budgetByNode.get(progress.nodeId());
            CumulativeActualCostEvidence actual = actualByNode.get(progress.nodeId());
            if (budget == null) {
                throw new IllegalArgumentException("Missing approved budget evidence for WBS node " + progress.nodeId());
            }
            if (actual == null) {
                throw new IllegalArgumentException("Missing approved actual-cost evidence for WBS node " + progress.nodeId());
            }
            if (!command.currencyCode().equals(budget.currencyCode())
                    || !command.currencyCode().equals(actual.currencyCode())) {
                throw new IllegalArgumentException("Cost evidence currency does not match requested report currency");
            }
            metrics.add(EarnedValueCalculator.calculate(
                    budget, actual, command.reportDate(), progress.plannedPercent(), progress.actualPercent()));
        }

        Optional<EarnedValuePortfolioReport> portfolio = Optional.empty();
        boolean portfolioRatiosDefined = metrics.stream().map(EarnedValueMetrics::actualCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add).signum() > 0
                && metrics.stream().map(EarnedValueMetrics::plannedValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add).signum() > 0;
        if (portfolioRatiosDefined) {
            portfolio = Optional.of(EarnedValuePortfolioReport.fromNodes(metrics));
        }
        return new Result(metrics, portfolio);
    }

    private static Map<UUID, BudgetedCostEvidence> indexBudgets(
            List<BudgetedCostEvidence> evidence, Command command) {
        Map<UUID, BudgetedCostEvidence> indexed = new HashMap<>();
        Set<UUID> requested = new HashSet<>(command.progress().stream().map(NodeProgress::nodeId).toList());
        for (BudgetedCostEvidence item : evidence) {
            Objects.requireNonNull(item, "Budget evidence cannot contain null");
            if (!command.tenantId().equals(item.tenantId())
                    || !command.structureId().equals(item.structureId())
                    || !command.baselineId().equals(item.baselineId())
                    || !item.isApproved()) {
                throw new IllegalArgumentException("Budget source returned evidence outside the approved report scope");
            }
            if (!requested.contains(item.nodeId())) {
                throw new IllegalArgumentException("Budget source returned an unrequested WBS node");
            }
            if (indexed.putIfAbsent(item.nodeId(), item) != null) {
                throw new IllegalArgumentException("Budget source returned duplicate evidence for a WBS node");
            }
        }
        return indexed;
    }

    private static Map<UUID, CumulativeActualCostEvidence> indexActualCosts(
            List<CumulativeActualCostEvidence> evidence, Command command) {
        Map<UUID, CumulativeActualCostEvidence> indexed = new HashMap<>();
        Set<UUID> requested = new HashSet<>(command.progress().stream().map(NodeProgress::nodeId).toList());
        for (CumulativeActualCostEvidence item : evidence) {
            Objects.requireNonNull(item, "Actual-cost evidence cannot contain null");
            if (!command.tenantId().equals(item.tenantId())
                    || !command.structureId().equals(item.structureId())
                    || !command.reportDate().equals(item.asOfDate())
                    || !item.isApproved()) {
                throw new IllegalArgumentException("Actual-cost source returned evidence outside the approved report scope");
            }
            if (!requested.contains(item.nodeId())) {
                throw new IllegalArgumentException("Actual-cost source returned an unrequested WBS node");
            }
            if (indexed.putIfAbsent(item.nodeId(), item) != null) {
                throw new IllegalArgumentException("Actual-cost source returned duplicate evidence for a WBS node");
            }
        }
        return indexed;
    }

    public record Command(
            UUID tenantId,
            UUID structureId,
            UUID baselineId,
            LocalDate reportDate,
            String currencyCode,
            List<NodeProgress> progress) {
        public Command {
            Objects.requireNonNull(tenantId, "Tenant id cannot be null");
            Objects.requireNonNull(structureId, "Structure id cannot be null");
            Objects.requireNonNull(baselineId, "Baseline id cannot be null");
            Objects.requireNonNull(reportDate, "Report date cannot be null");
            currencyCode = requireText(currencyCode, "Currency code");
            try {
                java.util.Currency.getInstance(currencyCode);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Currency code must be a supported ISO-4217 code", exception);
            }
            progress = List.copyOf(Objects.requireNonNull(progress, "Progress inputs cannot be null"));
            if (progress.isEmpty()) {
                throw new IllegalArgumentException("At least one WBS node progress input is required");
            }
            Set<UUID> nodeIds = new HashSet<>();
            for (NodeProgress item : progress) {
                Objects.requireNonNull(item, "Progress inputs cannot contain null");
                if (!nodeIds.add(item.nodeId())) {
                    throw new IllegalArgumentException("Duplicate WBS node in progress inputs");
                }
            }
        }
    }

    public record NodeProgress(UUID nodeId, BigDecimal plannedPercent, BigDecimal actualPercent) {
        public NodeProgress {
            Objects.requireNonNull(nodeId, "WBS node id cannot be null");
            validatePercent(plannedPercent, "Planned percent");
            validatePercent(actualPercent, "Actual percent");
        }

        private static void validatePercent(BigDecimal value, String field) {
            Objects.requireNonNull(value, field + " cannot be null");
            if (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException(field + " must be between 0 and 100");
            }
        }
    }

    public record Result(List<EarnedValueMetrics> nodes, Optional<EarnedValuePortfolioReport> portfolio) {
        public Result {
            nodes = List.copyOf(Objects.requireNonNull(nodes, "Node metrics cannot be null"));
            portfolio = Objects.requireNonNull(portfolio, "Portfolio result cannot be null");
            if (nodes.isEmpty()) throw new IllegalArgumentException("At least one node metric is required");
            if (portfolio.isPresent() && !portfolio.get().nodes().equals(nodes)) {
                throw new IllegalArgumentException("Portfolio report must contain the returned node metrics");
            }
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
