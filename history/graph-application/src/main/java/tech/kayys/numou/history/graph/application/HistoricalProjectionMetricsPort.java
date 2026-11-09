package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionOperationalStore.OperationalSnapshot;

/**
 * Vendor-neutral metrics boundary. Runtime adapters may export these values to
 * Micrometer, OpenTelemetry, Prometheus, or an application-specific telemetry system.
 * Implementations should avoid using tenant IDs as metric labels.
 */
@FunctionalInterface
public interface HistoricalProjectionMetricsPort {
    void record(OperationalSnapshot snapshot);
}
