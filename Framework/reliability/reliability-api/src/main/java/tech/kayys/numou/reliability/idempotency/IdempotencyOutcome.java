package tech.kayys.numou.reliability.idempotency;

import java.time.Instant;
import java.util.Objects;

/**
 * The outcome of claiming an idempotency key (base01.md §P1-11, §P1-15).
 *
 * <p>Two distinct results, because the caller's reaction differs:
 *
 * <pre>
 *   PROCEED  - first time, run the work
 *   DUPLICATE - already ran, return the recorded result
 * </pre>
 *
 * @param claimed    whether the caller now owns the right to execute
 * @param firstSeenAt when the key was first claimed
 * @param resultRef  reference to the recorded result, if any
 */
public record IdempotencyOutcome(boolean claimed, Instant firstSeenAt, String resultRef) {

    public IdempotencyOutcome {
        Objects.requireNonNull(firstSeenAt, "firstSeenAt cannot be null");
        resultRef = resultRef == null ? "" : resultRef;
    }

    /** First arrival: the caller should execute the work. */
    public static IdempotencyOutcome proceed(Instant firstSeenAt) {
        return new IdempotencyOutcome(true, firstSeenAt, "");
    }

    /** A repeat arrival: the work already ran, return the stored result. */
    public static IdempotencyOutcome duplicate(Instant firstSeenAt, String resultRef) {
        return new IdempotencyOutcome(false, firstSeenAt, resultRef);
    }

    public boolean isFirstTime() {
        return claimed;
    }

    public boolean isDuplicate() {
        return !claimed;
    }
}
