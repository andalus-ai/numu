package tech.kayys.numou.security.vault.spi;

import java.time.Instant;
import java.util.UUID;

/** Bounded, tenant-scoped operational counters; contains no secret or subject identifiers. */
public record VaultPolicyOperationalMetrics(UUID tenantId, long activeRevision,
        long ruleCount, long decisionsAllowed, long decisionsDenied,
        Instant windowStart, Instant measuredAt) {
    public VaultPolicyOperationalMetrics {
        if (tenantId == null || windowStart == null || measuredAt == null)
            throw new IllegalArgumentException("tenant and metric timestamps are required");
        if (activeRevision < 0 || ruleCount < 0 || decisionsAllowed < 0 || decisionsDenied < 0)
            throw new IllegalArgumentException("metric counters must not be negative");
        if (measuredAt.isBefore(windowStart))
            throw new IllegalArgumentException("measuredAt must not precede windowStart");
    }
}
