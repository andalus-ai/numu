package tech.kayys.numou.reliability.outbox;

import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A business event persisted atomically with the state change that produced it. */
public record OutboxEntry(
        UUID entryId,
        EventEnvelope<? extends BusinessEvent> envelope,
        Instant createdAt,
        Instant availableAt,
        int attempts,
        boolean published,
        String lastError) {

    public static final int MAX_ATTEMPTS = 10;
    public static final int MAX_ERROR_LENGTH = 2000;

    /** Compatibility constructor for callers that do not yet supply diagnostics. */
    public OutboxEntry(UUID entryId, EventEnvelope<? extends BusinessEvent> envelope,
                       Instant createdAt, Instant availableAt, int attempts, boolean published) {
        this(entryId, envelope, createdAt, availableAt, attempts, published, null);
    }

    public OutboxEntry {
        Objects.requireNonNull(entryId, "entryId cannot be null");
        Objects.requireNonNull(envelope, "envelope cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(availableAt, "availableAt cannot be null");
        if (attempts < 0) throw new IllegalArgumentException("attempts cannot be negative");
        lastError = bounded(lastError);
    }

    /** Creates a fresh, unpublished entry ready for the relay. */
    public static OutboxEntry pending(EventEnvelope<? extends BusinessEvent> envelope) {
        final var now = Instant.now();
        return new OutboxEntry(UUID.randomUUID(), envelope, now, now, 0, false, null);
    }

    /** Returns a copy recorded after a failed delivery attempt, preserving prior diagnostics. */
    public OutboxEntry recordFailure(Instant retryAt) {
        return recordFailure(retryAt, lastError);
    }

    /** Returns a copy recorded after a failed delivery attempt with its diagnostic. */
    public OutboxEntry recordFailure(Instant retryAt, String error) {
        if (published) throw new IllegalStateException("A published entry cannot be retried");
        return new OutboxEntry(entryId, envelope, createdAt, retryAt, attempts + 1, false, error);
    }

    /** Whether the relay should give up instead of retrying. */
    public boolean exhausted() {
        return attempts >= MAX_ATTEMPTS;
    }

    /** Returns a copy recorded after a successful delivery and clears stale failure details. */
    public OutboxEntry markPublished() {
        return new OutboxEntry(entryId, envelope, createdAt, createdAt, attempts, true, null);
    }

    public boolean isDue(Instant now) {
        return !published && !availableAt.isAfter(now);
    }

    private static String bounded(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) return value;
        return value.substring(0, MAX_ERROR_LENGTH);
    }
}