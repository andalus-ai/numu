package tech.kayys.numou.affiliate.spi;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped lease, delivery, and operational-query contracts for Affiliate integration events. */
public interface AffiliateEventOutboxStore {
    CompletionStage<List<ClaimedEvent>> claimPending(String tenantId, String workerId,
            Instant now, Duration lease, int limit);
    CompletionStage<Void> markDelivered(String tenantId, UUID eventId, String workerId, Instant now);
    CompletionStage<Void> reschedule(String tenantId, UUID eventId, String workerId,
            String reason, Instant retryAt, Instant now, int maxAttempts);

    CompletionStage<Boolean> redriveDeadLetter(String tenantId, UUID eventId, String actorId,
            String reason, String idempotencyKey, String correlationId, Instant now);

    /** Keyset cursor for a stable descending (occurredAt, eventId) dead-letter ordering. */
    record DeadLetterCursor(Instant occurredAt, UUID eventId) {
        public DeadLetterCursor {
            Objects.requireNonNull(occurredAt, "occurredAt");
            Objects.requireNonNull(eventId, "eventId");
        }
    }

    /** Payload-free operational summary; raw event payloads and provider errors are deliberately excluded. */
    record DeadLetterSummary(UUID eventId, String aggregateType, UUID aggregateId,
            String eventType, int attempts, Instant occurredAt, long version) {}

    record DeadLetterPage(List<DeadLetterSummary> items, DeadLetterCursor nextCursor) {
        public DeadLetterPage {
            items = List.copyOf(items);
        }
    }

    /** Global, low-cardinality health snapshot. It intentionally contains no tenant identifiers. */
    record OutboxHealthSnapshot(long pending, long pendingDue, long inFlight,
            long expiredLeases, long deadLetter, long oldestDueAgeSeconds) {
        public OutboxHealthSnapshot {
            if (pending < 0 || pendingDue < 0 || inFlight < 0 || expiredLeases < 0
                    || deadLetter < 0 || oldestDueAgeSeconds < 0) {
                throw new IllegalArgumentException("outbox health values cannot be negative");
            }
        }
    }

    CompletionStage<DeadLetterPage> findDeadLetters(String tenantId, DeadLetterCursor before, int limit);

    CompletionStage<OutboxHealthSnapshot> healthSnapshot();

    record ClaimedEvent(String tenantId, UUID id, String aggregateType, UUID aggregateId,
            String eventType, String eventKey, String payload, int attempts,
            String leaseOwner, Instant leaseUntil) {}
}
