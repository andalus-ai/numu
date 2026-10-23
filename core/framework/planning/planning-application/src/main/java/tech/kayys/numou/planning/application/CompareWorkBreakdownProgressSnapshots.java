package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.WorkBreakdownProgressSnapshotRepository;
import tech.kayys.numou.planning.domain.ProgressVariance;
import tech.kayys.numou.planning.domain.WorkBreakdownProgressSnapshot;
import java.util.Objects;
import java.util.UUID;

/** Compares two tenant-scoped snapshots without mutating historical evidence. */
public final class CompareWorkBreakdownProgressSnapshots {
    private final WorkBreakdownProgressSnapshotRepository snapshots;
    public CompareWorkBreakdownProgressSnapshots(WorkBreakdownProgressSnapshotRepository snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots, "Snapshot repository cannot be null");
    }
    public ProgressVariance execute(UUID tenantId, UUID baselineSnapshotId, UUID currentSnapshotId) {
        if (tenantId == null || baselineSnapshotId == null || currentSnapshotId == null)
            throw new IllegalArgumentException("Tenant and snapshot identifiers are required");
        if (baselineSnapshotId.equals(currentSnapshotId)) throw new IllegalArgumentException("Snapshots must differ");
        WorkBreakdownProgressSnapshot baseline = snapshots.findById(tenantId, baselineSnapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Baseline snapshot was not found for tenant"));
        WorkBreakdownProgressSnapshot current = snapshots.findById(tenantId, currentSnapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Current snapshot was not found for tenant"));
        if (!baseline.structureId().equals(current.structureId()) || !baseline.nodeId().equals(current.nodeId()))
            throw new IllegalArgumentException("Snapshots must describe the same WBS structure and node");
        if (current.asOfDate().isBefore(baseline.asOfDate()))
            throw new IllegalArgumentException("Current snapshot must not precede baseline");
        return new ProgressVariance(tenantId, baseline.structureId(), baseline.nodeId(),
                baseline.snapshotId(), current.snapshotId(), baseline.asOfDate(), current.asOfDate(),
                baseline.completionPercent(), current.completionPercent(),
                current.completionPercent().subtract(baseline.completionPercent()));
    }
}
