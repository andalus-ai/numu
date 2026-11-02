package tech.kayys.numou.event.spi.port;

import io.smallrye.mutiny.Uni;
import java.util.UUID;

/** Durable, tenant-scoped coordination boundary for dead-letter replay. */
public interface DeadLetterReplayStorePort {
    /** Atomically acquires a lease and records its audit entry. */
    Uni<ReplayClaim> beginReplay(ReplayRequest request);
    /** Completes only the current lease identified by claimToken. */
    Uni<Void> completeReplay(ReplayClaim claim);
    /** Records a failed attempt only if claimToken still owns the lease. */
    Uni<Void> failReplay(ReplayClaim claim, String failureCode);

    record ReplayRequest(String deadLetterRecordId, UUID tenantId, String actorId,
                         String reason, String eventType, String payloadSha256) {
        public ReplayRequest {
            if (deadLetterRecordId == null || deadLetterRecordId.isBlank() || deadLetterRecordId.length() > 500)
                throw new IllegalArgumentException("deadLetterRecordId must be non-blank and at most 500 characters");
            if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
            if (actorId == null || actorId.isBlank() || actorId.length() > 200)
                throw new IllegalArgumentException("actorId must be non-blank and at most 200 characters");
            if (reason == null || reason.isBlank() || reason.length() > 1000)
                throw new IllegalArgumentException("reason must be non-blank and at most 1000 characters");
            if (eventType == null || eventType.isBlank() || eventType.length() > 200)
                throw new IllegalArgumentException("eventType must be non-blank and at most 200 characters");
            if (payloadSha256 == null || !payloadSha256.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("payloadSha256 must be a SHA-256 hex digest");
        }
    }
    record ReplayClaim(String deadLetterRecordId, UUID tenantId, String claimToken,
                       int attempt, ClaimStatus status) {
        public ReplayClaim {
            if (deadLetterRecordId == null || tenantId == null || claimToken == null
                    || attempt < 0 || status == null) throw new IllegalArgumentException("Invalid replay claim");
        }
    }
    enum ClaimStatus { ACQUIRED, ALREADY_COMPLETED, IN_PROGRESS }
}
