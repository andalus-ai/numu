package tech.kayys.numou.accounting.application.close;

import tech.kayys.numou.accounting.domain.close.CloseCycleId;

/**
 * Application boundary to already-persisted Syirkah stages.
 * Implementations must commit each operation durably before returning its reference.
 */
public interface SyirkahCloseOperations {
    String calculateAndPersistResult(CloseCycleId cycleId, String actor);
    String allocateAndPersistResult(CloseCycleId cycleId, String actor);
    String approveDistribution(CloseCycleId cycleId, String approver);
    String postApprovedDistribution(CloseCycleId cycleId, String poster);
}
