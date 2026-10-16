package tech.kayys.numou.foundation.application.runtime.lifecycle;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Result of the graceful shutdown sequence (config02.md §P4-08 #13).
 */
public record ShutdownResult(
        boolean successful,
        Instant startedAt,
        Instant completedAt,
        List<ShutdownFailure> failures
) {
    public ShutdownResult {
        Objects.requireNonNull(startedAt, "startedAt cannot be null");
        Objects.requireNonNull(completedAt, "completedAt cannot be null");
        failures = failures != null ? List.copyOf(failures) : List.of();
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }
}
