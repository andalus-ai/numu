package tech.kayys.numou.history.graph.spi;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Scope-isolated lifecycle contract for disposable graph projection generations.
 * The active pointer is changed with compare-and-set semantics by the provider.
 */
public interface HistoricalProjectionGenerationStore {
    Generation create(UUID tenantId, UUID generationId, Instant now);
    Optional<Generation> find(UUID tenantId, UUID generationId);
    Optional<Generation> active(UUID tenantId);
    boolean markReady(UUID tenantId, UUID generationId, Instant now);
    boolean activate(UUID tenantId, UUID generationId, UUID expectedActiveGenerationId, Instant now);

    enum Status { BUILDING, READY, ACTIVE, RETAINED }

    record Generation(UUID generationId, UUID tenantId, Status status,
                      Instant createdAt, Instant updatedAt) {}
}
