package tech.kayys.numou.history.graph.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only inspection contract for a rebuildable graph projection. */
public interface HistoricalProjectionReconciliationPort {
    Optional<ProjectionState> find(UUID tenantId, UUID relationshipId);
    List<UUID> listRelationshipIds(UUID tenantId, UUID afterExclusive, int limit);
    record ProjectionState(long revision, String eventKey, String payloadHash) {}
}
