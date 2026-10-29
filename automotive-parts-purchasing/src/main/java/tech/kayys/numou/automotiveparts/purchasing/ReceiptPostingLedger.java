package tech.kayys.numou.automotiveparts.purchasing;

/**
 * Durable lifecycle for the cross-module goods-receipt posting attempt.
 * Inventory's command receipt remains the authority for movement idempotency;
 * this ledger makes accepted work discoverable and retryable after process restart.
 */
public interface ReceiptPostingLedger {
    PostingRecord begin(String tenantId, String receiptId, String payloadFingerprint);

    void markPosted(String tenantId, String receiptId, String payloadFingerprint, String movementId);

    void markRetryable(String tenantId, String receiptId, String payloadFingerprint, String failure);

    enum Status { PENDING, POSTED }

    record PostingRecord(String tenantId, String receiptId, String payloadFingerprint,
                         Status status, String movementId, int attempts, String lastFailure) {}
}
