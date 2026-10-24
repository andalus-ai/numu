package tech.kayys.numou.planning.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import tech.kayys.numou.planning.domain.CumulativeActualCostEvidence;

/**
 * Port for retrieving reconciled actual-cost evidence at a reporting date.
 * Implementations must enforce tenant isolation and return only evidence approved for use.
 */
public interface CumulativeActualCostEvidenceSource {
    List<CumulativeActualCostEvidence> findApproved(UUID tenantId, UUID structureId, LocalDate asOfDate, List<UUID> nodeIds);
}
