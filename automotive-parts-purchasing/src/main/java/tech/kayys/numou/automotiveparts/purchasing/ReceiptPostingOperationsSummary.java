package tech.kayys.numou.automotiveparts.purchasing;

import java.time.Instant;

/** Tenant-scoped operational counts; counts are read from one SQL statement snapshot. */
public record ReceiptPostingOperationsSummary(
        long pending,
        long posted,
        long deadLetter,
        Instant oldestDeadLetterAt) {
}
