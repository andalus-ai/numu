package tech.kayys.numou.planning.application.port;

import tech.kayys.numou.planning.domain.ProgressSnapshotDecision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped append-only audit trail for snapshot review decisions. */
public interface ProgressSnapshotDecisionRepository {
    void append(ProgressSnapshotDecision decision);
    List<ProgressSnapshotDecision> findHistory(UUID tenantId, UUID snapshotId);
    Optional<ProgressSnapshotDecision> findLatest(UUID tenantId, UUID snapshotId);
}
