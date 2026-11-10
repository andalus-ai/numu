package tech.kayys.numou.history.graph.spi;

import java.time.Instant;
import java.util.UUID;

/** Append-only operator audit for reconciliation and projection repair operations. */
public interface HistoricalProjectionRepairAuditStore {
    UUID start(UUID tenantId, UUID actorId, int pageSize, Instant occurredAt);
    void complete(UUID tenantId, UUID actorId, UUID operationId, RepairOutcome outcome, Instant occurredAt);
    void fail(UUID tenantId, UUID actorId, UUID operationId, String reason, Instant occurredAt);

    record RepairOutcome(long canonicalChecked, long missing, long stale, long divergent,
                         long orphaned, long repaired) {}
}
