package tech.kayys.numou.queuemanagement.spi;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-scoped, payload-free operational counters for queue coordination.
 * Counts are a point-in-time snapshot and are not a substitute for a transactional read model.
 */
public record QueueOperationalSnapshot(
        UUID tenantId,
        Instant capturedAt,
        long waitingEntries,
        long activeEntries,
        long pendingOutbox,
        long dueOutbox,
        long leasedOutbox,
        long expiredLeases,
        long deadLetteredOutbox,
        long oldestPendingAgeSeconds) {

    public QueueOperationalSnapshot {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(capturedAt, "capturedAt");
        if (waitingEntries < 0 || activeEntries < 0 || pendingOutbox < 0 || dueOutbox < 0
                || leasedOutbox < 0 || expiredLeases < 0 || deadLetteredOutbox < 0
                || oldestPendingAgeSeconds < 0) {
            throw new IllegalArgumentException("operational counters must not be negative");
        }
    }
}
