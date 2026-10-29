package tech.kayys.numou.notification.application;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Bounded exponential retry delay with jitter; attempt count is one-based. */
public final class NotificationDeliveryRetryPolicy {
    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final double jitterFraction;

    public NotificationDeliveryRetryPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay, double jitterFraction) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        this.baseDelay = positive(baseDelay, "baseDelay");
        this.maxDelay = positive(maxDelay, "maxDelay");
        if (maxDelay.compareTo(baseDelay) < 0) throw new IllegalArgumentException("maxDelay must be >= baseDelay");
        if (!Double.isFinite(jitterFraction) || jitterFraction < 0 || jitterFraction > 0.5)
            throw new IllegalArgumentException("jitterFraction must be between 0 and 0.5");
        this.maxAttempts = maxAttempts;
        this.jitterFraction = jitterFraction;
    }

    public boolean exhausted(int attemptCount) { return attemptCount >= maxAttempts; }

    public Duration delayForAttempt(int attemptCount) {
        if (attemptCount < 1) throw new IllegalArgumentException("attemptCount must be positive");
        long baseMillis = baseDelay.toMillis();
        long capMillis = maxDelay.toMillis();
        int shift = Math.min(attemptCount - 1, 62);
        long exponential;
        try { exponential = Math.multiplyExact(baseMillis, 1L << shift); }
        catch (ArithmeticException overflow) { exponential = capMillis; }
        long capped = Math.min(exponential, capMillis);
        if (jitterFraction == 0 || capped == 0) return Duration.ofMillis(capped);
        double factor = 1.0 - jitterFraction + ThreadLocalRandom.current().nextDouble() * (2.0 * jitterFraction);
        return Duration.ofMillis(Math.max(1, Math.min(capMillis, (long) (capped * factor))));
    }

    private static Duration positive(Duration duration, String name) {
        Objects.requireNonNull(duration, name + " is required");
        if (duration.isNegative() || duration.isZero() || duration.toMillis() < 1)
            throw new IllegalArgumentException(name + " must be at least one millisecond");
        return duration;
    }
}
