package tech.kayys.numou.workspace.application.port;

import java.time.Instant;
import java.util.UUID;

/** Tenant-scoped aggregate health read; implementations must not return delivery payloads. */
@FunctionalInterface
public interface WorkspaceMentionOutboxHealthStore {
    Snapshot snapshot(UUID tenantId, Instant now);

    record Snapshot(long pending, long due, long leased, long expiredLeases, long dead,
                    Instant oldestPendingAt) {
        public Snapshot {
            if (pending < 0 || due < 0 || leased < 0 || expiredLeases < 0 || dead < 0
                    || due > pending || expiredLeases > leased) {
                throw new IllegalArgumentException("invalid outbox health counters");
            }
        }
    }
}
