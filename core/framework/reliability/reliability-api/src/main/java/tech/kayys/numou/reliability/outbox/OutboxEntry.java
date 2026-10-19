package tech.kayys.numou.reliability.outbox;

import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A business event written in the same transaction as the state change
 * that produced it (base01.md §P1-14).
 *
 * <p>This is what makes "persist, then publish" atomic: either both the
 * aggregate change and the event land, or neither does. A relay later
 * drains pending entries to the broker - and retries until they go.
 *
 * @param entryId    unique row identity
 * @param envelope   the event to publish
 * @param createdAt  when the owning transaction committed
 * @param availableAt earliest time the relay may attempt delivery
 * @param attempts   how many relay attempts have been made
 * @param published  true once a broker acknowledged it
 */
public record OutboxEntry(
        UUID entryId,
        EventEnvelope<? extends BusinessEvent> envelope,
        Instant createdAt,
        Instant availableAt,
        int attempts,
        boolean published) {

    public static final int MAX_ATTEMPTS = 10;

    public OutboxEntry {
        Objects.requireNonNull(entryId, "entryId cannot be null");
        Objects.requireNonNull(envelope, "envelope cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(availableAt, "availableAt cannot be null");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts cannot be negative");
        }
    }

    /** Creates a fresh, unpublished entry ready for the relay. */
    public static OutboxEntry pending(EventEnvelope<? extends BusinessEvent> envelope) {
        final var now = Instant.now();
        return new OutboxEntry(UUID.randomUUID(), envelope, now, now, 0, false);
    }

    /** Returns a copy recorded after a failed delivery attempt. */
    public OutboxEntry recordFailure(Instant retryAt) {
        if (published) {
            throw new IllegalStateException("A published entry cannot be retried");
        }
        return new OutboxEntry(entryId, envelope, createdAt, retryAt, attempts + 1, false);
    }

    /** Returns a copy recorded after a successful delivery. */
    public OutboxEntry markPublished() {
        return new OutboxEntry(entryId, envelope, createdAt, createdAt, attempts, true);
    }

    /** Whether the relay should give up instead of retrying. */
    public boolean exhausted() {
        return attempts >= MAX_ATTEMPTS;
    }

    public boolean isDue(Instant now) {
        return !published && !availableAt.isAfter(now);
    }
}
