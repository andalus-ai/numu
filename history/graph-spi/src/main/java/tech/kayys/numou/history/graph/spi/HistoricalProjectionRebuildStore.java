package tech.kayys.numou.history.graph.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable cursor and lifecycle contract for replaying the immutable projection outbox.
 * Null tenant scope denotes the shared public corpus; non-null scope is tenant-private.
 */
public interface HistoricalProjectionRebuildStore {
    RebuildRun start(UUID tenantId, Instant now);
    Optional<RebuildRun> find(UUID tenantId, UUID runId);
    List<ReplayEvent> nextPage(UUID tenantId, UUID runId, int limit);
    /**
     * Legacy checkpoint API retained for source compatibility. New workers must use
     * advanceFenced; this method cannot protect against pause/resume ABA races.
     */
    @Deprecated
    boolean advance(UUID tenantId, UUID runId, long expectedCursor, long nextCursor, Instant now);

    /** Legacy completion API; new workers must use completeFenced. */
    @Deprecated
    boolean complete(UUID tenantId, UUID runId, long expectedCursor, Instant now);

    default boolean completeFenced(UUID tenantId, UUID runId, long expectedCursor,
                                   long expectedLifecycleVersion, Instant now) {
        throw new UnsupportedOperationException("fenced rebuild completion is not supported");
    }

    /**
     * Advances a page only if both cursor and lifecycle version still match the
     * worker's snapshot. Implementations must atomically increment lifecycleVersion.
     */
    default boolean advanceFenced(UUID tenantId, UUID runId, long expectedCursor, long nextCursor,
                                  long expectedLifecycleVersion, Instant now) {
        throw new UnsupportedOperationException("fenced rebuild checkpoints are not supported");
    }

    /**
     * Changes state and appends the actor-attributed audit row in one transaction.
     * The update is compare-and-set on both status and lifecycle version.
     */
    default boolean transition(UUID tenantId, UUID runId, UUID actorId, Action action,
                               long expectedLifecycleVersion, RebuildRun.Status expectedStatus,
                               RebuildRun.Status targetStatus, String detail, Instant now) {
        throw new UnsupportedOperationException("rebuild lifecycle transitions are not supported");
    }

    enum Action { PAUSE, RESUME, CANCEL, RETRY, FAIL }

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
