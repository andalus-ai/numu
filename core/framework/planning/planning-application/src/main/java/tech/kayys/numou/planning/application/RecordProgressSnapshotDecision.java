package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.ProgressSnapshotDecisionRepository;
import tech.kayys.numou.planning.application.port.WorkBreakdownProgressSnapshotRepository;
import tech.kayys.numou.planning.domain.ProgressSnapshotDecision;
import java.util.Objects;

/** Records a decision only after verifying the snapshot exists in the tenant scope. */
public final class RecordProgressSnapshotDecision {
    private final WorkBreakdownProgressSnapshotRepository snapshots;
    private final ProgressSnapshotDecisionRepository decisions;
    public RecordProgressSnapshotDecision(WorkBreakdownProgressSnapshotRepository snapshots,
            ProgressSnapshotDecisionRepository decisions) {
        this.snapshots = Objects.requireNonNull(snapshots, "Snapshot repository cannot be null");
        this.decisions = Objects.requireNonNull(decisions, "Decision repository cannot be null");
    }
    public void execute(ProgressSnapshotDecision decision) {
        Objects.requireNonNull(decision, "Decision cannot be null");
        if (snapshots.findById(decision.tenantId(), decision.snapshotId()).isEmpty())
            throw new IllegalArgumentException("Snapshot was not found for tenant");
        decisions.append(decision);
    }
}
