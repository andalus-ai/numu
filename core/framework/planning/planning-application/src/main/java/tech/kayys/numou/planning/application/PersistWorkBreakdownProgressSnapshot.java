package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.WorkBreakdownProgressSnapshotRepository;
import tech.kayys.numou.planning.domain.WorkBreakdownProgressSnapshot;
import java.util.Objects;

/** Application boundary for storing a validated, immutable calculation result. */
public final class PersistWorkBreakdownProgressSnapshot {
    private final WorkBreakdownProgressSnapshotRepository snapshots;
    public PersistWorkBreakdownProgressSnapshot(WorkBreakdownProgressSnapshotRepository snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots, "Snapshot repository cannot be null");
    }
    public void execute(WorkBreakdownProgressSnapshot snapshot) {
        snapshots.create(Objects.requireNonNull(snapshot, "Snapshot cannot be null"));
    }
}
