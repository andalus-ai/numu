package tech.kayys.numou.history.graph.application;

import java.util.UUID;

/** Bridges a failed page projection to an actor-attributed durable lifecycle transition. */
@FunctionalInterface
public interface HistoricalProjectionRebuildFailurePort {
    void markFailed(UUID tenantId, UUID runId, String reason);
}
