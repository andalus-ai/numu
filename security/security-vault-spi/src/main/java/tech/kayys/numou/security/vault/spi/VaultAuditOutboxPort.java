package tech.kayys.numou.security.vault.spi;

import io.smallrye.mutiny.Uni;
import java.time.Instant;
import java.util.List;

/** Lease-based at-least-once delivery boundary for durable vault audit events. */
public interface VaultAuditOutboxPort {
    Uni<List<VaultAuditOutboxEvent>> claimDue(String workerId, Instant now,
                                               Instant leaseUntil, int limit);
    Uni<Void> markPublished(java.util.UUID eventId, String workerId, Instant publishedAt);
    Uni<Void> reschedule(java.util.UUID eventId, String workerId, Instant nextAttemptAt,
                         String safeError, boolean deadLetter);
}
