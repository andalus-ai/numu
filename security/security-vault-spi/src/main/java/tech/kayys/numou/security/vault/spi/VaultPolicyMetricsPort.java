package tech.kayys.numou.security.vault.spi;

import io.smallrye.mutiny.Uni;
import java.time.Instant;
import java.util.UUID;

/** Tenant-scoped policy operational metrics over an explicit bounded time window. */
public interface VaultPolicyMetricsPort {
    Uni<VaultPolicyOperationalMetrics> load(UUID tenantId, Instant windowStart, Instant measuredAt);
}
