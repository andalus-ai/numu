package tech.kayys.numou.foundation.application.runtime;

import java.time.Instant;
import java.util.Objects;

/**
 * Audit record of a lifecycle transition.
 */
public record LifecycleTransition(
        ApplicationRuntimeState from,
        ApplicationRuntimeState to,
        Instant timestamp
) {
    public LifecycleTransition {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    public static LifecycleTransition of(ApplicationRuntimeState from, ApplicationRuntimeState to) {
        return new LifecycleTransition(from, to, Instant.now());
    }
}
