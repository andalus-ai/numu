package tech.kayys.numou.construction.application.consultancy;

import java.time.Instant;

/** Tenant-scoped operational counts and oldest outstanding-operation timestamps. */
public record ConsultancyEvidenceOperationsSnapshot(
        long pendingCount,
        long retryableFailureCount,
        long terminalFailureCount,
        long linkedCount,
        Instant oldestPendingUpdatedAt,
        Instant oldestRetryableFailureUpdatedAt,
        Instant oldestTerminalFailureUpdatedAt) {
    public ConsultancyEvidenceOperationsSnapshot {
        if (pendingCount < 0 || retryableFailureCount < 0 || terminalFailureCount < 0 || linkedCount < 0) {
            throw new IllegalArgumentException("Operation counts cannot be negative");
        }
    }

    public static ConsultancyEvidenceOperationsSnapshot empty() {
        return new ConsultancyEvidenceOperationsSnapshot(0, 0, 0, 0, null, null, null);
    }
}
