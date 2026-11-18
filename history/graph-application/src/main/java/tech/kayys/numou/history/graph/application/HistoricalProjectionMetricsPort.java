package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionOperationalStore.OperationalSnapshot;

/** Provider-neutral metrics boundary; never use tenant/run/event IDs as metric labels. */
@FunctionalInterface
public interface HistoricalProjectionMetricsPort {
    void record(OperationalSnapshot snapshot);
    default void recordRebuildProgress(RebuildMetric metric) {}
    default void recordReconciliation(ReconciliationMetric metric) {}
    default void recordGenerationRun(GenerationRunMetric metric) {}

    record RebuildMetric(long projectedEvents, long cursorSequence, long highWaterSequence, boolean completed) {}
    record ReconciliationMetric(long canonicalChecked, long missing, long stale, long divergent, long orphaned, long repaired) {}
    record GenerationRunMetric(String status, int failureCount) {}
}
