package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionOperationalStore.OperationalSnapshot;

/**
 * Vendor-neutral metrics boundary. Runtime adapters may export these values to
 * Micrometer, OpenTelemetry, Prometheus, or an application-specific telemetry system.
 * Implementations should avoid using tenant IDs, run IDs, or event IDs as metric labels.
 */
@FunctionalInterface
public interface HistoricalProjectionMetricsPort {
    void record(OperationalSnapshot snapshot);

    default void recordRebuildProgress(RebuildMetric metric) {}
    default void recordReconciliation(ReconciliationMetric metric) {}
    default void recordGenerationRun(GenerationRunMetric metric) {}

    record RebuildMetric(long projectedEvents, long cursorSequence, long highWaterSequence,
                         boolean completed) {}
    record ReconciliationMetric(long canonicalChecked, long missing, long stale,
                                long divergent, long orphaned, long repaired) {}
    record GenerationRunMetric(String status, int failureCount) {}
}
