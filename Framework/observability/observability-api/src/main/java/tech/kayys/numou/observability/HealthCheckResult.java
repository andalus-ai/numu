package tech.kayys.syirkah.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable outcome of executing an individual health check (config02.md §P4-07 #8).
 */
public record HealthCheckResult(
        String checkId,
        HealthStatus.Status status,
        String message,
        Instant checkedAt,
        Duration duration
) {
    public HealthCheckResult {
        Objects.requireNonNull(checkId, "checkId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(checkedAt, "checkedAt cannot be null");
        message = message == null ? "" : message;
        duration = duration == null ? Duration.ZERO : duration;
    }

    public static HealthCheckResult up(String checkId, String message, Duration duration) {
        return new HealthCheckResult(checkId, HealthStatus.Status.UP, message, Instant.now(), duration);
    }

    public static HealthCheckResult degraded(String checkId, String message, Duration duration) {
        return new HealthCheckResult(checkId, HealthStatus.Status.DEGRADED, message, Instant.now(), duration);
    }

    public static HealthCheckResult down(String checkId, String message, Duration duration) {
        return new HealthCheckResult(checkId, HealthStatus.Status.DOWN, message, Instant.now(), duration);
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

    public boolean isHealthy() {
        return isUp() || isDegraded();
    }
}
