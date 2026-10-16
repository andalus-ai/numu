package tech.kayys.syirkah.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Aggregated health evaluation report (config02.md §P4-07 #9).
 */
public record HealthReport(
        HealthStatus.Status status,
        List<HealthCheckResult> checks,
        Instant evaluatedAt,
        Duration duration
) {
    public HealthReport {
        checks = checks != null ? List.copyOf(checks) : List.of();
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt cannot be null");
        duration = duration != null ? duration : Duration.ZERO;
    }

    public boolean isUp() {
        return status == HealthStatus.Status.UP;
    }

    public boolean isDegraded() {
        return status == HealthStatus.Status.DEGRADED;
    }

    public boolean isDown() {
        return status == HealthStatus.Status.DOWN;
    }
}
