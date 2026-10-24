package tech.kayys.numou.planning.application.port.out;

import java.util.List;
import java.util.UUID;
import tech.kayys.numou.planning.domain.BudgetedCostEvidence;

/**
 * Port for retrieving budget evidence from an approved baseline, finance system, BoQ adapter,
 * or another budget source. Implementations must enforce tenant isolation.
 */
public interface BudgetedCostEvidenceSource {
    List<BudgetedCostEvidence> findApproved(UUID tenantId, UUID structureId, UUID baselineId, List<UUID> nodeIds);
}
