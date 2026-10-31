package tech.kayys.numou.automotiveparts.purchasing;

import java.time.Instant;
import java.util.UUID;

/** Immutable audit event emitted by PostgreSQL for every successful operator requeue. */
public record ReceiptPostingRequeueAudit(
        UUID auditId,
        String tenantId,
        String receiptId,
        String payloadFingerprint,
        String actorId,
        String reason,
        Instant occurredAt) {
}
