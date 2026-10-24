package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.ProgressSnapshotDecisionRepository;
import tech.kayys.numou.planning.application.port.WorkBreakdownProgressBaselineRepository;
import tech.kayys.numou.planning.application.port.WorkBreakdownProgressSnapshotRepository;
import tech.kayys.numou.planning.domain.ProgressSnapshotDecision;
import tech.kayys.numou.planning.domain.ProgressVariance;
import tech.kayys.numou.planning.domain.WorkBreakdownProgressBaseline;
import tech.kayys.numou.planning.domain.WorkBreakdownProgressSnapshot;

import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Compares an approved current snapshot with the immutable snapshot captured by a named baseline.
 * The latest decision is authoritative, so a later rejection supersedes an earlier approval.
 */
public final class CompareProgressSnapshotToBaseline {
    private final WorkBreakdownProgressBaselineRepository baselines;
    private final WorkBreakdownProgressSnapshotRepository snapshots;
    private final ProgressSnapshotDecisionRepository decisions;

    public CompareProgressSnapshotToBaseline(
            WorkBreakdownProgressBaselineRepository baselines,
            WorkBreakdownProgressSnapshotRepository snapshots,
            ProgressSnapshotDecisionRepository decisions) {
        this.baselines = Objects.requireNonNull(baselines, "Baseline repository cannot be null");
        this.snapshots = Objects.requireNonNull(snapshots, "Snapshot repository cannot be null");
        this.decisions = Objects.requireNonNull(decisions, "Decision repository cannot be null");
    }

    public ProgressVariance execute(UUID tenantId, UUID baselineId, UUID currentSnapshotId) {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(baselineId, "Baseline id cannot be null");
        Objects.requireNonNull(currentSnapshotId, "Current snapshot id cannot be null");

        WorkBreakdownProgressBaseline baseline = baselines.findById(tenantId, baselineId)
                .orElseThrow(() -> new IllegalArgumentException("Baseline was not found for tenant"));
        WorkBreakdownProgressSnapshot source = snapshots.findById(tenantId, baseline.sourceSnapshotId())
                .orElseThrow(() -> new IllegalStateException("Baseline source snapshot is missing"));
        WorkBreakdownProgressSnapshot current = snapshots.findById(tenantId, currentSnapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Current snapshot was not found for tenant"));

        if (!source.structureId().equals(current.structureId()) || !source.nodeId().equals(current.nodeId())
                || !baseline.structureId().equals(current.structureId()) || !baseline.nodeId().equals(current.nodeId())) {
            throw new IllegalArgumentException("Current snapshot and baseline must have the same WBS scope");
        }
        if (current.snapshotId().equals(source.snapshotId())) {
            throw new IllegalArgumentException("Current snapshot must differ from the baseline source snapshot");
        }
        if (current.asOfDate().isBefore(baseline.effectiveDate())) {
            throw new IllegalArgumentException("Current snapshot date cannot precede baseline effective date");
        }

        ProgressSnapshotDecision latest = decisions.findLatest(tenantId, currentSnapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Current snapshot must be approved before comparison"));
        if (latest.outcome() != ProgressSnapshotDecision.Outcome.APPROVED) {
            throw new IllegalArgumentException("Current snapshot's latest decision is not approval");
        }

        return new ProgressVariance(tenantId, baseline.structureId(), baseline.nodeId(),
                source.snapshotId(), current.snapshotId(), source.asOfDate(), current.asOfDate(),
                source.completionPercent(), current.completionPercent(),
                current.completionPercent().subtract(source.completionPercent()).setScale(2, RoundingMode.HALF_UP));
    }
}
