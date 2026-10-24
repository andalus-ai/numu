package tech.kayys.numou.planning.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import tech.kayys.numou.planning.domain.BudgetedCostEvidence;
import tech.kayys.numou.planning.domain.CumulativeActualCostEvidence;

/**
 * Unified tenant-scoped read contract for a finance, ERP, procurement, or BoQ integration.
 * Implementations must return only approved records and must enforce tenant isolation in
 * the underlying query, not merely filter after reading.
 */
public interface CostEvidenceReader {
    List<BudgetedCostEvidence> findApprovedBudgets(
            UUID tenantId, UUID structureId, UUID baselineId, List<UUID> nodeIds);

    List<CumulativeActualCostEvidence> findApprovedActualCosts(
            UUID tenantId, UUID structureId, LocalDate asOfDate, List<UUID> nodeIds);
}
