package tech.kayys.numou.automotiveparts.purchasing;

import java.time.Instant;

/** Operator-safe projection of a terminal goods-receipt posting failure. */
public record DeadLetterReceiptPosting(
        String receiptId,
        int attempts,
        int maxAttempts,
        String lastFailure,
        String terminalFailureReason,
        Instant deadLetteredAt,
        Instant createdAt,
        Instant updatedAt) {
}
