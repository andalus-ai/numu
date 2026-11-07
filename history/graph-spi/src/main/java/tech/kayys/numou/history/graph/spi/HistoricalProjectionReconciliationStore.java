package tech.kayys.numou.history.graph.spi;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;
import java.util.List;
import java.util.UUID;

/** Read-only canonical view of the latest retained projection event per relationship. */
public interface HistoricalProjectionReconciliationStore {
    List<ClaimedProjectionEvent> latestPage(UUID tenantId, UUID afterRelationshipIdExclusive, int limit);
    boolean exists(UUID tenantId, UUID relationshipId);
}
