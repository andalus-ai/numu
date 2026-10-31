package tech.kayys.numou.automotiveparts.purchasing;

import java.time.Instant;
import java.util.List;

/** Durable lifecycle for cross-module goods-receipt posting. Inventory owns movement idempotency. */
public interface ReceiptPostingLedger {
    int DEFAULT_MAX_ATTEMPTS = 5;

    PostingRecord begin(String tenantId, String receiptId, String payloadFingerprint);
    default PostingRecord begin(String tenantId, String receiptId, String payloadFingerprint, int maxAttempts) {
        return begin(tenantId, receiptId, payloadFingerprint);
    }

    void markPosted(String tenantId, String receiptId, String payloadFingerprint, String movementId);
    void markRetryable(String tenantId, String receiptId, String payloadFingerprint, String failure);
    default void markRetryable(String tenantId, String receiptId, String payloadFingerprint,
                               String failure, int maxAttempts) {
        markRetryable(tenantId, receiptId, payloadFingerprint, failure);
    }

    List<PostingRecord> findPending(Instant updatedBefore, int limit);
    List<PostingRecord> claimPending(Instant updatedBefore, String workerId, Instant leaseUntil, int limit);

    /** Compare-and-set transition; only DEAD_LETTER records may be requeued. */
    void requeue(String tenantId, String receiptId, String fingerprint,
                 String actorId, String reason);

    enum Status { PENDING, POSTED, DEAD_LETTER }

    /** attempts is the number of failed posting attempts, not the number of begin/replay calls. */
    record PostingRecord(String tenantId, String receiptId, String payloadFingerprint,
                        Status status, String movementId, int attempts, String lastFailure,
                        Instant deadLetteredAt, Instant requeuedAt, String requeuedBy, String requeueReason) {}
}
