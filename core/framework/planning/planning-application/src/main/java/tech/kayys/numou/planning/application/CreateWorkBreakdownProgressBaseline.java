package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.ProgressSnapshotDecisionRepository;
import tech.kayys.numou.planning.application.port.WorkBreakdownProgressBaselineRepository;
import tech.kayys.numou.planning.application.port.WorkBreakdownProgressSnapshotRepository;
import tech.kayys.numou.planning.domain.ProgressSnapshotDecision;
import tech.kayys.numou.planning.domain.WorkBreakdownProgressBaseline;
import tech.kayys.numou.planning.domain.WorkBreakdownProgressSnapshot;
import java.util.Objects;

/** Creates a baseline only from a snapshot whose latest governance decision is approval. */
public final class CreateWorkBreakdownProgressBaseline {
    private final WorkBreakdownProgressSnapshotRepository snapshots;
    private final ProgressSnapshotDecisionRepository decisions;
    private final WorkBreakdownProgressBaselineRepository baselines;

    public CreateWorkBreakdownProgressBaseline(WorkBreakdownProgressSnapshotRepository snapshots,
            ProgressSnapshotDecisionRepository decisions, WorkBreakdownProgressBaselineRepository baselines) {
        this.snapshots = Objects.requireNonNull(snapshots, "Snapshot repository cannot be null");
        this.decisions = Objects.requireNonNull(decisions, "Decision repository cannot be null");
        this.baselines = Objects.requireNonNull(baselines, "Baseline repository cannot be null");
    }

    public void execute(WorkBreakdownProgressBaseline baseline) {
        Objects.requireNonNull(baseline, "Baseline cannot be null");
        WorkBreakdownProgressSnapshot snapshot = snapshots.findById(baseline.tenantId(), baseline.sourceSnapshotId())
                .orElseThrow(() -> new IllegalArgumentException("Source snapshot was not found for tenant"));
        if (!snapshot.structureId().equals(baseline.structureId()) || !snapshot.nodeId().equals(baseline.nodeId()))
            throw new IllegalArgumentException("Baseline scope must match its source snapshot");
        if (baseline.effectiveDate().isBefore(snapshot.asOfDate()))
            throw new IllegalArgumentException("Baseline effective date cannot precede the source snapshot date");
        ProgressSnapshotDecision latest = decisions.findLatest(baseline.tenantId(), baseline.sourceSnapshotId())
                .orElseThrow(() -> new IllegalArgumentException("Source snapshot must be approved before baselining"));
        if (latest.outcome() != ProgressSnapshotDecision.Outcome.APPROVED)
            throw new IllegalArgumentException("Source snapshot's latest decision is not approval");
        baselines.create(baseline);
    }
}
