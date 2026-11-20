package tech.kayys.numou.feedback.application.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Runtime-neutral outbox dispatcher. A scheduler or worker in the host runtime calls
 * dispatchOnce; this class deliberately has no Quarkus, Kafka or broker dependency.
 */
public final class FeedbackOutboxRelay {
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

    private final FeedbackOutboxStore store;
    private final FeedbackEventPublisher publisher;
    private final int batchSize;
    private final int maximumAttempts;
    private final Duration leaseDuration;

    public FeedbackOutboxRelay(FeedbackOutboxStore store, FeedbackEventPublisher publisher,
            int batchSize, int maximumAttempts, Duration leaseDuration) {
        this.store = Objects.requireNonNull(store, "store");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        if (batchSize < 1 || batchSize > 500) throw new IllegalArgumentException("batchSize must be 1..500");
        if (maximumAttempts < 1) throw new IllegalArgumentException("maximumAttempts must be positive");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) throw new IllegalArgumentException("leaseDuration must be positive");
        this.batchSize = batchSize;
        this.maximumAttempts = maximumAttempts;
    }

    /** Returns the number of events successfully published during this poll. */
    public int dispatchOnce(Instant now) {
        Objects.requireNonNull(now, "now");
        var messages = store.claimPending(now, now.plus(leaseDuration), batchSize);
        int published = 0;
        for (var message : messages) {
            try {
                publisher.publish(message);
                store.markPublished(message.eventId(), message.leaseToken(), now);
                published++;
            } catch (Exception failure) {
                store.recordFailure(message.eventId(), message.leaseToken(), sanitize(failure),
                        now.plus(retryDelay(message.attempts() + 1)), maximumAttempts);
            }
        }
        return published;
    }

    static Duration retryDelay(int attempt) {
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
        long seconds = 5;
        for (int i = 1; i < attempt && seconds < MAX_RETRY_DELAY.toSeconds(); i++) {
            seconds = Math.min(MAX_RETRY_DELAY.toSeconds(), seconds * 3);
        }
        return Duration.ofSeconds(seconds);
    }

    private static String sanitize(Exception failure) {
        String message = failure.getMessage();
        String value = failure.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message.strip());
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
