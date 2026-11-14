package tech.kayys.numou.history.graph.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable, tenant-scoped lifecycle and cursor contract for projection rebuilds. */
public interface HistoricalProjectionRebuildStore {
    RebuildRun start(UUID tenantId, Instant now);
    Optional<RebuildRun> find(UUID tenantId, UUID runId);
    List<ReplayEvent> nextPage(UUID tenantId, UUID runId, int limit);
    boolean advance(UUID tenantId, UUID runId, long expectedCursor, long nextCursor, Instant now);
    boolean complete(UUID tenantId, UUID runId, long expectedCursor, Instant now);

    /** Fenced checkpoint: a stale worker cannot advance after any lifecycle transition. */
    boolean advanceFenced(UUID tenantId, UUID runId, long expectedCursor, long nextCursor,
                         long expectedLifecycleVersion, Instant now);

    /** Atomically changes lifecycle state and appends the operator audit record. */
    boolean transition(UUID tenantId, UUID runId, UUID actorId, String action,
                       long expectedLifecycleVersion, RebuildRun.Status expectedStatus,
                       RebuildRun.Status targetStatus, String detail, Instant now);

    /** Marks a still-running run failed without moving its last successful cursor. */
    boolean fail(UUID tenantId, UUID runId, long expectedLifecycleVersion, String detail, Instant now);

    record RebuildRun(UUID runId, UUID tenantId, long highWaterSequence,
                      long cursorSequence, Status status, Instant createdAt, Instant updatedAt,
                      long lifecycleVersion, String failureDetail) {
        public RebuildRun(UUID runId, UUID tenantId, long highWaterSequence,
                          long cursorSequence, Status status, Instant createdAt, Instant updatedAt) {
            this(runId, tenantId, highWaterSequence, cursorSequence, status, createdAt, updatedAt, 0, "");
        }
        public enum Status { RUNNING, PAUSED, CANCELLED, FAILED, COMPLETED }
    }

    record ReplayEvent(long sequence, UUID eventId, String eventKey, String eventType,
                       int schemaVersion, UUID tenantId, UUID aggregateId, long aggregateRevision,
                       String payload, Instant occurredAt) {}
}
