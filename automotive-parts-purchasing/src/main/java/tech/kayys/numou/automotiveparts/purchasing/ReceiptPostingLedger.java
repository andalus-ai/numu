package tech.kayys.numou.automotiveparts.purchasing;

import java.time.Instant;
import java.util.List;

/**
 * Durable lifecycle for the cross-module goods-receipt posting attempt.
 * Inventory's command receipt remains the authority for movement idempotency;
 * this ledger makes accepted work discoverable and retryable after process restart.
 */
public interface ReceiptPostingLedger {
    PostingRecord begin(String tenantId, String receiptId, String payloadFingerprint);

    void markPosted(String tenantId, String receiptId, String payloadFingerprint, String movementId);

    void markRetryable(String tenantId, String receiptId, String payloadFingerprint, String failure);

    /** Pending attempts that have not been updated since the cutoff, oldest first. */
    List<PostingRecord> findPending(Instant updatedBefore, int limit);

    /** Atomically claims stale pending records for one worker; expired leases are reclaimable. */
    List<PostingRecord> claimPending(Instant updatedBefore, String workerId, Instant leaseUntil, int limit);

    enum Status { PENDING, POSTED }

    record PostingRecord(String tenantId, String receiptId, String payloadFingerprint,
                         Status status, String movementId, int attempts, String lastFailure) {}
}
