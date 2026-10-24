package tech.kayys.numou.planning.application.adapter;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import tech.kayys.numou.planning.application.port.out.BudgetedCostEvidenceSource;
import tech.kayys.numou.planning.application.port.out.CostEvidenceReader;
import tech.kayys.numou.planning.application.port.out.CumulativeActualCostEvidenceSource;
import tech.kayys.numou.planning.domain.BudgetedCostEvidence;
import tech.kayys.numou.planning.domain.CumulativeActualCostEvidence;

/**
 * Adapter exposing one cost-evidence reader through the two planning output ports.
 * Keeps the planning use case independent of finance/ERP/BoQ APIs and their data models.
 */
public final class CostEvidenceSourceAdapter
        implements BudgetedCostEvidenceSource, CumulativeActualCostEvidenceSource {
    private final CostEvidenceReader reader;

    public CostEvidenceSourceAdapter(CostEvidenceReader reader) {
        this.reader = Objects.requireNonNull(reader, "Cost evidence reader cannot be null");
    }

    @Override
    public List<BudgetedCostEvidence> findApproved(
            UUID tenantId, UUID structureId, UUID baselineId, List<UUID> nodeIds) {
        requireScope(tenantId, structureId, nodeIds);
        Objects.requireNonNull(baselineId, "Baseline id cannot be null");
        List<UUID> requestedNodes = List.copyOf(nodeIds);
        List<BudgetedCostEvidence> result = Objects.requireNonNull(
                reader.findApprovedBudgets(tenantId, structureId, baselineId, requestedNodes),
                "Cost evidence reader returned null budget results");
        return List.copyOf(result);
    }

    @Override
    public List<CumulativeActualCostEvidence> findApproved(
            UUID tenantId, UUID structureId, LocalDate asOfDate, List<UUID> nodeIds) {
        requireScope(tenantId, structureId, nodeIds);
        Objects.requireNonNull(asOfDate, "Actual-cost as-of date cannot be null");
        List<UUID> requestedNodes = List.copyOf(nodeIds);
        List<CumulativeActualCostEvidence> result = Objects.requireNonNull(
                reader.findApprovedActualCosts(tenantId, structureId, asOfDate, requestedNodes),
                "Cost evidence reader returned null actual-cost results");
        return List.copyOf(result);
    }

    private static void requireScope(UUID tenantId, UUID structureId, List<UUID> nodeIds) {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(nodeIds, "WBS node ids cannot be null");
        if (nodeIds.isEmpty()) throw new IllegalArgumentException("At least one WBS node id is required");
        if (nodeIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("WBS node ids cannot contain null");
        }
        if (nodeIds.stream().distinct().count() != nodeIds.size()) {
            throw new IllegalArgumentException("WBS node ids cannot contain duplicates");
        }
    }
}
