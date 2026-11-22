package tech.kayys.numou.history.source.spi;

import tech.kayys.numou.history.source.domain.SourceIngestionLease;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable tenant-scoped work queue.
 * Every transition is fenced by both the worker identity and the current lease token.
 */
public interface SourceIngestionDispatchRepository {
    Optional<SourceIngestionLease> claimNext(
            UUID tenantId, String workerRef, Instant now, Instant leaseUntil);

    boolean complete(UUID tenantId, UUID ingestionId, String workerRef, UUID leaseToken, Instant completedAt);

    boolean scheduleRetry(
            UUID tenantId, UUID ingestionId, String workerRef, UUID leaseToken,
            Instant availableAt, Instant failedAt, String error);

    boolean deadLetter(
            UUID tenantId, UUID ingestionId, String workerRef, UUID leaseToken,
            Instant failedAt, String error);
}
