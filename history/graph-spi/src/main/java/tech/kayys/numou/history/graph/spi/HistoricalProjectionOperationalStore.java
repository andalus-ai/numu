package tech.kayys.numou.history.graph.spi;

import java.time.Instant;
import java.util.UUID;

/** Read-only operational counters for one public or tenant-private projection scope. */
public interface HistoricalProjectionOperationalStore {
    OperationalSnapshot snapshot(UUID tenantId, Instant now);

    record OperationalSnapshot(UUID tenantId, long pendingEvents, long leasedEvents,
            long deadLetterEvents, long oldestPendingAgeSeconds, long maxUnpublishedAgeSeconds,
            long runningRebuilds, long rebuildEventsRemaining, long oldestRebuildAgeSeconds,
            Instant capturedAt) {}
}
