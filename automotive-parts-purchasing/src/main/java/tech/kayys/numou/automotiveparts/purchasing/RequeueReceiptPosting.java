package tech.kayys.numou.automotiveparts.purchasing;

import java.util.Objects;

/** Explicit, tenant-scoped and audited operator recovery of a dead-letter receipt posting. */
public final class RequeueReceiptPosting {
    private final ReceiptPostingLedger ledger;
    private final ReceiptPostingRequeueAuthorizer authorizer;

    public RequeueReceiptPosting(ReceiptPostingLedger ledger, ReceiptPostingRequeueAuthorizer authorizer) {
        this.ledger = Objects.requireNonNull(ledger, "ledger is required");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer is required");
    }

    public void execute(String tenantId, String receiptId, String fingerprint, String actorId, String reason) {
        require(tenantId, "tenantId");
        require(receiptId, "receiptId");
        require(fingerprint, "fingerprint");
        require(actorId, "actorId");
        require(reason, "reason");
        if (!authorizer.mayRequeue(tenantId, actorId, receiptId)) {
            throw new SecurityException("Actor is not authorized to requeue this tenant's receipt posting");
        }
        ledger.requeue(tenantId, receiptId, fingerprint, actorId.trim(), reason.trim());
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
