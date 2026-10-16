package tech.kayys.syirkah.observability;

import java.time.Instant;
import java.util.Objects;

/**
 * Cached snapshot of an evaluated HealthReport (config02.md §P4-07 #22).
 */
public record HealthSnapshot(
        HealthReport report,
        Instant capturedAt
) {
    public HealthSnapshot {
        Objects.requireNonNull(report, "report cannot be null");
        capturedAt = capturedAt != null ? capturedAt : Instant.now();
    }

    public static HealthSnapshot of(HealthReport report) {
        return new HealthSnapshot(report, Instant.now());
    }
}
