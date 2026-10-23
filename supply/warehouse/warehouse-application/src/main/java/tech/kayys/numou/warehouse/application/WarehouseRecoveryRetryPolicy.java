package tech.kayys.numou.warehouse.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Deterministic, bounded retry policy for durable warehouse recovery attempts.
 *
 * <p>The attempt count is the number of executions already started (the initial
 * prepared attempt has count 1). Call {@link #decide(int, Instant)} after a failed
 * attempt, persist the returned eligibility time with the recovery journal, and
 * do not claim the attempt before that time.</p>
 *
 * <p>This class deliberately does not persist state or schedule work. Persistence
 * adapters must durably store the decision and filter recovery claims by it.</p>
 */
public final class WarehouseRecoveryRetryPolicy {
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final int multiplier;

    public WarehouseRecoveryRetryPolicy(int maxAttempts, Duration initialBackoff,
                                        Duration maxBackoff, int multiplier) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        this.initialBackoff = positive(initialBackoff, "initialBackoff");
        this.maxBackoff = positive(maxBackoff, "maxBackoff");
        if (maxBackoff.compareTo(initialBackoff) < 0)
            throw new IllegalArgumentException("maxBackoff must be >= initialBackoff");
        if (multiplier < 1) throw new IllegalArgumentException("multiplier must be >= 1");
        this.maxAttempts = maxAttempts;
        this.multiplier = multiplier;
    }

    /**
     * Calculate whether another execution is permitted after a failure.
     * At exhaustion, nextEligibleAt is empty and the caller must mark the record
     * terminal/dead-lettered rather than retrying it.
     */
    public RetryDecision decide(int attemptsStarted, Instant failedAt) {
        if (attemptsStarted < 1) throw new IllegalArgumentException("attemptsStarted must be positive");
        Objects.requireNonNull(failedAt, "failedAt");
        if (attemptsStarted >= maxAttempts) {
            return new RetryDecision(false, java.util.Optional.empty(),
                    "Maximum recovery attempts reached (" + maxAttempts + ")");
        }
        Duration delay = backoffFor(attemptsStarted);
        return new RetryDecision(true, java.util.Optional.of(failedAt.plus(delay)), null);
    }

    public Duration backoffFor(int attemptsStarted) {
        if (attemptsStarted < 1) throw new IllegalArgumentException("attemptsStarted must be positive");
        Duration delay = initialBackoff;
        for (int i = 1; i < attemptsStarted && delay.compareTo(maxBackoff) < 0; i++) {
            try {
                delay = delay.multipliedBy(multiplier);
            } catch (ArithmeticException overflow) {
                return maxBackoff;
            }
            if (delay.compareTo(maxBackoff) > 0) return maxBackoff;
        }
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    public record RetryDecision(boolean retryAllowed,
                                java.util.Optional<Instant> nextEligibleAt,
                                String terminalReason) {
        public RetryDecision {
            Objects.requireNonNull(nextEligibleAt, "nextEligibleAt");
            if (retryAllowed && (nextEligibleAt.isEmpty() || terminalReason != null))
                throw new IllegalArgumentException("Allowed retry requires a next time and no terminal reason");
            if (!retryAllowed && (nextEligibleAt.isPresent() || terminalReason == null || terminalReason.isBlank()))
                throw new IllegalArgumentException("Exhausted retry requires a terminal reason and no next time");
        }
    }
}
