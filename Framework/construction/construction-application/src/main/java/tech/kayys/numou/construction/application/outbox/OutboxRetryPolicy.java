package tech.kayys.syirkah.construction.application.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Deterministic capped exponential retry schedule; add jitter in the relay adapter if needed. */
public record OutboxRetryPolicy(Duration initialDelay, Duration maximumDelay, int maxAttempts) {
    public OutboxRetryPolicy {
        Objects.requireNonNull(initialDelay, "Initial delay cannot be null");
        Objects.requireNonNull(maximumDelay, "Maximum delay cannot be null");
        if (initialDelay.isNegative() || initialDelay.isZero())
            throw new IllegalArgumentException("Initial delay must be positive");
        if (maximumDelay.compareTo(initialDelay) < 0)
            throw new IllegalArgumentException("Maximum delay must be >= initial delay");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
    }

    /** attempts is the count after the latest failed delivery, starting at 1. */
    public Instant nextAttemptAt(Instant failureTime, int attempts) {
        Objects.requireNonNull(failureTime, "Failure time cannot be null");
        if (attempts < 1) throw new IllegalArgumentException("attempts must be >= 1");
        long initialMillis = initialDelay.toMillis();
        long maxMillis = maximumDelay.toMillis();
        int shifts = Math.min(attempts - 1, 62);
        long delayMillis;
        if (initialMillis > (Long.MAX_VALUE >> shifts)) delayMillis = maxMillis;
        else delayMillis = Math.min(maxMillis, initialMillis << shifts);
        return failureTime.plusMillis(delayMillis);
    }

    public boolean isTerminal(int attempts) {
        if (attempts < 0) throw new IllegalArgumentException("attempts cannot be negative");
        return attempts >= maxAttempts;
    }
}
