package tech.kayys.numou.planning.application.port;

import tech.kayys.numou.planning.domain.WorkBreakdownProgressSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped append-only persistence port for calculated WBS progress evidence. */
public interface WorkBreakdownProgressSnapshotRepository {
    /** Persists the snapshot and all contribution inputs atomically. */
    void create(WorkBreakdownProgressSnapshot snapshot);
    Optional<WorkBreakdownProgressSnapshot> findById(UUID tenantId, UUID snapshotId);
    /** Returns history newest reporting date first, bounded to one tenant and WBS node. */
    List<WorkBreakdownProgressSnapshot> findHistory(
            UUID tenantId, UUID structureId, UUID nodeId, LocalDate from, LocalDate through);
}
