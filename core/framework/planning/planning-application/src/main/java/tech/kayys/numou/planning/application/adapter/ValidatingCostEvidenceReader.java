package tech.kayys.numou.planning.application.adapter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import tech.kayys.numou.planning.application.port.out.CostEvidenceReader;
import tech.kayys.numou.planning.domain.BudgetedCostEvidence;
import tech.kayys.numou.planning.domain.CumulativeActualCostEvidence;

/**
 * Defensive decorator for cost-evidence providers.
 *
 * <p>Validates that returned rows match the exact request scope and contain only approved,
 * unique evidence. A persistence implementation must still enforce tenant isolation in its
 * query; this decorator is defense in depth, not an authorization boundary.</p>
 */
public final class ValidatingCostEvidenceReader implements CostEvidenceReader {
    private final CostEvidenceReader delegate;

    public ValidatingCostEvidenceReader(CostEvidenceReader delegate) {
        this.delegate = Objects.requireNonNull(delegate, "Cost evidence reader cannot be null");
    }

    @Override
    public List<BudgetedCostEvidence> findApprovedBudgets(
            UUID tenantId, UUID structureId, UUID baselineId, List<UUID> nodeIds) {
        validateRequest(tenantId, structureId, nodeIds);
        Objects.requireNonNull(baselineId, "Baseline id cannot be null");
        List<UUID> requested = List.copyOf(nodeIds);
        List<BudgetedCostEvidence> rows = Objects.requireNonNull(
                delegate.findApprovedBudgets(tenantId, structureId, baselineId, requested),
                "Cost evidence reader returned null budget results");
        Set<UUID> seen = new HashSet<>();
        for (BudgetedCostEvidence row : rows) {
            Objects.requireNonNull(row, "Budget evidence cannot contain null");
            if (!tenantId.equals(row.tenantId())
                    || !structureId.equals(row.structureId())
                    || !baselineId.equals(row.baselineId())
                    || !row.isApproved()) {
                throw new IllegalStateException("Budget evidence violates tenant, structure, baseline, or approval scope");
            }
            if (!requested.contains(row.nodeId())) {
                throw new IllegalStateException("Budget evidence contains an unrequested WBS node");
            }
            if (!seen.add(row.nodeId())) {
                throw new IllegalStateException("Budget evidence contains multiple rows for one WBS node");
            }
        }
        return List.copyOf(rows);
    }

    @Override
    public List<CumulativeActualCostEvidence> findApprovedActualCosts(
            UUID tenantId, UUID structureId, LocalDate asOfDate, List<UUID> nodeIds) {
        validateRequest(tenantId, structureId, nodeIds);
        Objects.requireNonNull(asOfDate, "Actual-cost as-of date cannot be null");
        List<UUID> requested = List.copyOf(nodeIds);
        List<CumulativeActualCostEvidence> rows = Objects.requireNonNull(
                delegate.findApprovedActualCosts(tenantId, structureId, asOfDate, requested),
                "Cost evidence reader returned null actual-cost results");
        Set<UUID> seen = new HashSet<>();
        for (CumulativeActualCostEvidence row : rows) {
            Objects.requireNonNull(row, "Actual-cost evidence cannot contain null");
            if (!tenantId.equals(row.tenantId())
                    || !structureId.equals(row.structureId())
                    || !asOfDate.equals(row.asOfDate())
                    || !row.isApproved()) {
                throw new IllegalStateException("Actual-cost evidence violates tenant, structure, date, or approval scope");
            }
            if (!requested.contains(row.nodeId())) {
                throw new IllegalStateException("Actual-cost evidence contains an unrequested WBS node");
            }
            if (!seen.add(row.nodeId())) {
                throw new IllegalStateException("Actual-cost evidence contains multiple rows for one WBS node");
            }
        }
        return List.copyOf(rows);
    }

    private static void validateRequest(UUID tenantId, UUID structureId, List<UUID> nodeIds) {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(nodeIds, "WBS node ids cannot be null");
        if (nodeIds.isEmpty()) {
            throw new IllegalArgumentException("At least one WBS node id is required");
        }
        if (nodeIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("WBS node ids cannot contain null");
        }
        if (nodeIds.stream().distinct().count() != nodeIds.size()) {
            throw new IllegalArgumentException("WBS node ids cannot contain duplicates");
        }
    }
}
