package tech.kayys.numou.developer.apicatalog.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;

/** Tenant-scoped durable lease queue for abandoned API specification artifacts. */
public interface ApiArtifactCleanupRepository {
    Uni<List<CleanupLease>> claimDue(TenantId tenant, String workerId, Instant now,
                                     Instant leaseUntil, int limit, int maximumAttempts);

    Uni<Boolean> isLeaseCurrent(TenantId tenant, CleanupLease lease, String workerId,
                                Instant now, int maximumAttempts);

    Uni<Void> markDeleted(TenantId tenant, CleanupLease lease, String workerId, Instant deletedAt);

    Uni<Void> recordFailure(TenantId tenant, CleanupLease lease, String workerId,
                            String failureCode, Instant retryAt, int maximumAttempts);

    record CleanupLease(String productId, String version, String artifactReference,
                        String sha256, int attempts, Instant leaseUntil) { }
}
