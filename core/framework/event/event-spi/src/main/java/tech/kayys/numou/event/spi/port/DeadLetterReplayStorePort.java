package tech.kayys.numou.event.spi.port;

import io.smallrye.mutiny.Uni;

import java.util.UUID;

/**
 * Durable, tenant-scoped coordination boundary for dead-letter replay.
 *
 * <p>Implementations must atomically create the audit entry and acquire a
 * lease keyed by (tenantId, deadLetterRecordId). A concurrent caller must
 * never receive ACQUIRED while an unexpired lease exists. COMPLETED records
 * are immutable and must never be acquired again. FAILED records may be
 * acquired again as an explicit new attempt, with the prior audit history
 * retained. The store must fence complete/fail operations by claimToken.
 */
public interface DeadLetterReplayStorePort {

    Uni<ReplayClaim> beginReplay(ReplayRequest request);

    /** Completes only the current lease identified by claimToken. */
    Uni<Void> completeReplay(ReplayClaim claim);

    /** Records a failed attempt only if claimToken still owns the lease. */
    Uni<Void> failReplay(ReplayClaim claim, String failureCode);

    record ReplayRequest(
            String deadLetterRecordId,
            UUID tenantId,
            String actorId,
            String reason,
            String eventType,
            String payloadSha256) { }

    record ReplayClaim(
            String deadLetterRecordId,
            UUID tenantId,
            String claimToken,
            int attempt,
            ClaimStatus status) { }

    enum ClaimStatus {
        ACQUIRED,
        ALREADY_COMPLETED,
        IN_PROGRESS
    }
}
