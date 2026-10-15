package tech.kayys.syirkah.construction.spi.outbox;

import io.smallrye.mutiny.Uni;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence boundary for a competing-consumer outbox relay.
 *
 * <p>Implementations must claim rows atomically (PostgreSQL can use
 * {@code FOR UPDATE SKIP LOCKED}) and must validate the lease token on every
 * acknowledgement. A claimed row can be delivered more than once; consumers
 * therefore still need idempotent handling.</p>
 */
public interface OutboxRelayStore {
    Uni<List<ClaimedOutboxEvent>> claimBatch(String workerId, Instant now, Instant leaseUntil, int limit);

    Uni<Boolean> markPublished(UUID eventId, UUID leaseToken, Instant publishedAt);

    Uni<Boolean> markFailed(UUID eventId, UUID leaseToken, Instant retryAt, String error, boolean terminal);

    record ClaimedOutboxEvent(
            UUID eventId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            Instant occurredAt,
            int attempts,
            UUID leaseToken
    ) {
        public ClaimedOutboxEvent {
            if (eventId == null || aggregateType == null || aggregateType.isBlank()
                    || aggregateId == null || eventType == null || eventType.isBlank()
                    || payload == null || occurredAt == null || attempts < 0 || leaseToken == null) {
                throw new IllegalArgumentException("Invalid claimed outbox event");
            }
        }
    }
}
