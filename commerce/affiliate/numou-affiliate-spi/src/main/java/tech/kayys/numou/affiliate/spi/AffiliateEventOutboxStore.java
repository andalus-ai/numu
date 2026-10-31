package tech.kayys.numou.affiliate.spi;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped lease and delivery lifecycle for Affiliate integration events. */
public interface AffiliateEventOutboxStore {
    CompletionStage<List<ClaimedEvent>> claimPending(String tenantId, String workerId,
            Instant now, Duration lease, int limit);
    CompletionStage<Void> markDelivered(String tenantId, UUID eventId, String workerId, Instant now);
    CompletionStage<Void> reschedule(String tenantId, UUID eventId, String workerId,
            String reason, Instant retryAt, Instant now, int maxAttempts);

    CompletionStage<Boolean> redriveDeadLetter(String tenantId, UUID eventId, String actorId,
            String reason, String idempotencyKey, String correlationId, Instant now);

    record ClaimedEvent(String tenantId, UUID id, String aggregateType, UUID aggregateId,
            String eventType, String eventKey, String payload, int attempts,
            String leaseOwner, Instant leaseUntil) {}
}
