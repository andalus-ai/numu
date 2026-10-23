package tech.kayys.numou.warehouse.application;

import java.time.Instant;
import java.util.Objects;

/** Tenant-scoped point-in-time counters for warehouse recovery operations. */
public record WarehouseRecoveryHealthSummary(
        String tenantId,
        Instant observedAt,
        long recoverableCount,
        long claimedCount,
        long reconciliationCount,
        long deadLetterCount,
        Instant oldestRecoverableCreatedAt,
        Instant oldestDeadLetterUpdatedAt) {
    public WarehouseRecoveryHealthSummary {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        tenantId = tenantId.trim();
        Objects.requireNonNull(observedAt, "observedAt");
        if (recoverableCount < 0 || claimedCount < 0 || reconciliationCount < 0 || deadLetterCount < 0)
            throw new IllegalArgumentException("Recovery counts cannot be negative");
        if (claimedCount > recoverableCount)
            throw new IllegalArgumentException("Claimed count cannot exceed recoverable count");
    }
}
