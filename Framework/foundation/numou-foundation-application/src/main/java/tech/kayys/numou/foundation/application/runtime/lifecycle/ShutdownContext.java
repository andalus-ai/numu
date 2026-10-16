package tech.kayys.numou.foundation.application.runtime.lifecycle;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Context holding start timestamp, deadline, and policy for a shutdown execution (config02.md §P4-08 #12).
 */
public record ShutdownContext(
        Instant startedAt,
        Instant deadline,
        ShutdownPolicy policy
) {
    public ShutdownContext {
        Objects.requireNonNull(startedAt, "startedAt cannot be null");
        Objects.requireNonNull(deadline, "deadline cannot be null");
        Objects.requireNonNull(policy, "policy cannot be null");
    }

    public static ShutdownContext of(ShutdownPolicy policy) {
        Instant now = Instant.now();
        return new ShutdownContext(now, now.plus(policy.totalTimeout()), policy);
    }

    public Duration remaining() {
        Duration rem = Duration.between(Instant.now(), deadline);
        return rem.isNegative() ? Duration.ZERO : rem;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(deadline);
    }
}
