package tech.kayys.numou.reliability.retry;

import tech.kayys.numou.reliability.failure.FailureClassification;

import java.time.Duration;
import java.util.Objects;

/**
 * Backoff policy for retryable failures (base01.md §P1-20).
 *
 * <p>Exponential with jitter, because deterministic backoff from many
 * workers turns a transient outage into a thundering herd.
 *
 * @param maxAttempts          total attempts including the first
 * @param initialDelay         delay before attempt 2
 * @param multiplier           growth factor per attempt
 * @param maxDelay             ceiling for the computed delay
 * @param jitterRatio          0.0..1.0 fraction of randomisation
 */
public record RetryPolicy(
        int maxAttempts,
        Duration initialDelay,
        double multiplier,
        Duration maxDelay,
        double jitterRatio) {

    /** Conservative default: 5 attempts, 100 ms doubling up to 30 s. */
    public static final RetryPolicy DEFAULT = new RetryPolicy(
            5,
            Duration.ofMillis(100),
            2.0d,
            Duration.ofSeconds(30),
            0.2d);

    /** Single-shot: no retries at all. */
    public static final RetryPolicy NONE = new RetryPolicy(
            1, Duration.ZERO, 1.0d, Duration.ZERO, 0.0d);

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        Objects.requireNonNull(initialDelay, "initialDelay cannot be null");
        Objects.requireNonNull(maxDelay, "maxDelay cannot be null");
        if (multiplier < 1.0d) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }
        if (jitterRatio < 0.0d || jitterRatio > 1.0d) {
            throw new IllegalArgumentException("jitterRatio must be within 0.0..1.0");
        }
    }

    /** Whether another attempt should be made after {@code attempt} (1-based). */
    public boolean shouldRetry(int attempt) {
        return attempt >= 1 && attempt < maxAttempts;
    }

    /** Whether a failure of this class may be retried at all. */
    public boolean allows(FailureClassification classification) {
        return classification == FailureClassification.RETRYABLE
                || classification == FailureClassification.RETRYABLE_IDEMPOTENT_ONLY;
    }

    /**
     * Delay before the next attempt, without jitter - deterministic core.
     */
    public Duration baseDelay(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1");
        }
        // Cap the exponent: past a point the delay would exceed maxDelay
        // anyway, and an unclamped power overflows a long.
        final var exponent = Math.min(attempt - 1, 30);
        final var computed = initialDelay.multipliedBy((long) Math.pow(multiplier, exponent));
        return computed.compareTo(maxDelay) > 0 ? maxDelay : computed;
    }

    /**
     * Delay with jitter applied, using a caller-supplied random source so
     * tests stay deterministic.
     */
    public Duration delayFor(int attempt, double randomFraction) {
        final var base = baseDelay(attempt);
        if (jitterRatio <= 0.0d) {
            return base;
        }
        final var fraction = Math.max(0.0d, Math.min(1.0d, randomFraction));
        final var jitter = 1.0d - jitterRatio + (jitterRatio * fraction);
        return Duration.ofNanos(Math.max(0L, (long) (base.toNanos() * jitter)));
    }
}
