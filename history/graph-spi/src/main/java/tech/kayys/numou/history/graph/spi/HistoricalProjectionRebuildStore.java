package tech.kayys.numou.history.graph.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable cursor contract for replaying the immutable PostgreSQL projection outbox.
 * The tenant scope is null for the shared public corpus and non-null for private data.
 */
public interface HistoricalProjectionRebuildStore {
    /** Creates or returns the rebuild identified by runId; repeated calls are idempotent. */
    RebuildRun start(UUID tenantId, UUID runId, Instant now);

    default RebuildRun start(UUID tenantId, Instant now) {
        return start(tenantId, UUID.randomUUID(), now);
    }

    Optional<RebuildRun> find(UUID tenantId, UUID runId);

    List<ReplayEvent> nextPage(UUID tenantId, UUID runId, int limit);

    boolean advance(UUID tenantId, UUID runId, long expectedCursor, long nextCursor, Instant now);

    boolean complete(UUID tenantId, UUID runId, long expectedCursor, Instant now);

    record RebuildRun(UUID runId, UUID tenantId, long highWaterSequence,
                      long cursorSequence, Status status, Instant createdAt, Instant updatedAt) {
        public enum Status { RUNNING, COMPLETED }
    }

    record ReplayEvent(long sequence, UUID eventId, String eventKey, String eventType,
                       int schemaVersion, UUID tenantId, UUID aggregateId, long aggregateRevision,
                       String payload, Instant occurredAt) {}
}