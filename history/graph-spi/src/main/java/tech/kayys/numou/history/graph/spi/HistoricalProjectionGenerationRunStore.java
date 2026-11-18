package tech.kayys.numou.history.graph.spi;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Durable saga state for one staged graph-generation rebuild and cutover. */
public interface HistoricalProjectionGenerationRunStore {
    Run create(UUID tenantId, UUID operationId, UUID generationId, UUID rebuildRunId,
               UUID expectedActiveGenerationId, Instant now);
    Optional<Run> find(UUID tenantId, UUID operationId);
    boolean transition(UUID tenantId, UUID operationId, Status expected, Status next, Instant now);
    void recordFailure(UUID tenantId, UUID operationId, String detail, Instant now);

    enum Status { PREPARING, REPLAYING, VALIDATING, READY, ACTIVATING, ACTIVE }

    record Run(UUID operationId, UUID tenantId, UUID generationId, UUID rebuildRunId,
               UUID expectedActiveGenerationId, Status status, int failureCount,
               String lastError, Instant createdAt, Instant updatedAt, Instant activatedAt) {}
}
