package tech.kayys.syirkah.observability;

import java.util.Objects;

/**
 * Health of one probe (base01.md §17).
 *
 * @param status    up, degraded, down
 * @param name      probe name, e.g. "database"
 * @param detail    optional human-readable detail
 */
public record HealthStatus(Status status, String name, String detail) {

    public enum Status {
        UP,
        DEGRADED,
        DOWN
    }

    public HealthStatus {
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        detail = detail == null ? "" : detail;
    }

    public static HealthStatus up(String name) {
        return new HealthStatus(Status.UP, name, "");
    }

    public static HealthStatus degraded(String name, String detail) {
        return new HealthStatus(Status.DEGRADED, name, detail);
    }

    public static HealthStatus down(String name, String detail) {
        return new HealthStatus(Status.DOWN, name, detail);
    }

    public boolean isHealthy() {
        return status == Status.UP || status == Status.DEGRADED;
    }
}
