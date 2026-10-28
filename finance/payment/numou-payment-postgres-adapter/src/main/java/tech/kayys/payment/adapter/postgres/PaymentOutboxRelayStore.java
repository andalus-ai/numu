package tech.kayys.payment.adapter.postgres;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistence contract for competing payment-outbox workers. */
public interface PaymentOutboxRelayStore {
    List<ClaimedEvent> claimBatch(String workerId, Instant now, Instant leaseUntil, int limit);

    boolean markPublished(UUID eventId, UUID leaseToken, Instant publishedAt);

    boolean markFailed(UUID eventId, UUID leaseToken, Instant retryAt, String error, boolean terminal);

    record ClaimedEvent(
            UUID eventId,
            String tenantId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            Instant occurredAt,
            int attempts,
            int maxAttempts,
            UUID leaseToken) {
        public ClaimedEvent {
            if (eventId == null || tenantId == null || tenantId.isBlank()
                    || aggregateType == null || aggregateType.isBlank()
                    || aggregateId == null || eventType == null || eventType.isBlank()
                    || payload == null || occurredAt == null || attempts < 1 || maxAttempts < 1
                    || attempts > maxAttempts || leaseToken == null) {
                throw new IllegalArgumentException("Invalid claimed payment outbox event");
            }
        }
    }
}
