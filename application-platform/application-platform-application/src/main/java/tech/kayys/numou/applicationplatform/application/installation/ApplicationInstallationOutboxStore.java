package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Lease-fenced, tenant-scoped delivery contract for committed installation transition events. */
public interface ApplicationInstallationOutboxStore {
    CompletionStage<List<ClaimedEvent>> claimDue(TenantId tenantId, String workerId,
            Instant now, Duration lease, int limit);
    CompletionStage<Boolean> markDelivered(TenantId tenantId, UUID eventId, String workerId,
            UUID leaseToken, Instant now);
    CompletionStage<RetryOutcome> retry(TenantId tenantId, UUID eventId, String workerId,
            UUID leaseToken, String stableFailureCode, Instant retryAt, Instant now, int maxAttempts);

    enum RetryOutcome { RESCHEDULED, DEAD_LETTERED, LEASE_LOST }

    record ClaimedEvent(UUID eventId, UUID installationId, String applicationId, String packageVersion,
            String packageDigest, String eventType, String toStatus, long revision, String actorId,
            String correlationId, String reasonCode, Instant occurredAt, int attempts, String workerId,
            UUID leaseToken, Instant leaseUntil) {}
}
