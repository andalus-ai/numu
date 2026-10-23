package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.WorkBreakdownProgressBaselineRepository;
import tech.kayys.numou.planning.application.port.WorkBreakdownProgressSnapshotRepository;
import tech.kayys.numou.planning.domain.ProgressVariance;
import tech.kayys.numou.planning.domain.WorkBreakdownProgressBaseline;
import tech.kayys.numou.planning.domain.WorkBreakdownProgressSnapshot;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Compares a current snapshot with the immutable snapshot captured by a named baseline. */
public final class CompareProgressSnapshotToBaseline {
    private final WorkBreakdownProgressBaselineRepository baselines;
    private final WorkBreakdownProgressSnapshotRepository snapshots;

    public CompareProgressSnapshotToBaseline(WorkBreakdownProgressBaselineRepository baselines,
            WorkBreakdownProgressSnapshotRepository snapshots) {
        this.baselines = Objects.requireNonNull(baselines, "Baseline repository cannot be null");
        this.snapshots = Objects.requireNonNull(snapshots, "Snapshot repository cannot be null");
    }

    public ProgressVariance execute(UUID tenantId, UUID baselineId, UUID currentSnapshotId) {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        WorkBreakdownProgressBaseline baseline = baselines.findById(tenantId, baselineId)
                .orElseThrow(() -> new IllegalArgumentException("Baseline was not found for tenant"));
        WorkBreakdownProgressSnapshot source = snapshots.findById(tenantId, baseline.sourceSnapshotId())
                .orElseThrow(() -> new IllegalStateException("Baseline source snapshot is missing"));
        WorkBreakdownProgressSnapshot current = snapshots.findById(tenantId, currentSnapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Current snapshot was not found for tenant"));
        if (!source.structureId().equals(current.structureId()) || !source.nodeId().equals(current.nodeId())
                || !baseline.structureId().equals(current.structureId()) || !baseline.nodeId().equals(current.nodeId()))
            throw new IllegalArgumentException("Current snapshot and baseline must have the same WBS scope");
        if (current.snapshotId().equals(source.snapshotId()))
            throw new IllegalArgumentException("Current snapshot must differ from the baseline source snapshot");
        if (current.asOfDate().isBefore(baseline.effectiveDate()))
            throw new IllegalArgumentException("Current snapshot date cannot precede baseline effective date");
        return new ProgressVariance(tenantId, baseline.structureId(), baseline.nodeId(),
                source.snapshotId(), current.snapshotId(), source.asOfDate(), current.asOfDate(),
                source.completionPercent(), current.completionPercent(),
                current.completionPercent().subtract(source.completionPercent()).setScale(2, java.math.RoundingMode.HALF_UP));
    }
}
