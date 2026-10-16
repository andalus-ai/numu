package tech.kayys.syirkah.observability;

import java.util.Objects;

/**
 * A validated, dotted metric name with optional dimensions.
 *
 * <p>Fixing a small set of well-known names early keeps dashboards stable:
 * renaming a metric is an operational outage, not a refactor.
 *
 * @param name  dotted metric name, e.g. {@code event.publish.duration}
 * @param tags  dimensions such as {@code tenant} or {@code eventType}
 */
public record MetricName(String name, java.util.Map<String, String> tags) {

    public MetricName {
        Objects.requireNonNull(name, "name cannot be null");
        if (!name.matches("[a-z][a-z0-9]*(\\.[a-z][a-z0-9_]*)+")) {
            throw new IllegalArgumentException(
                    "Metric name must be dotted lower-case, e.g. event.publish.duration");
        }
        tags = tags == null ? java.util.Map.of() : java.util.Map.copyOf(tags);
    }

    public static MetricName of(String name) {
        return new MetricName(name, java.util.Map.of());
    }

    public MetricName with(String key, String value) {
        final var extended = new java.util.LinkedHashMap<>(tags);
        extended.put(key, value);
        return new MetricName(name, extended);
    }
}
