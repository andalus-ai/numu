package tech.kayys.syirkah.construction.spi.boq;

import tech.kayys.syirkah.construction.domain.boq.BoqRevisionSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for immutable BOQ revision snapshots.
 *
 * Implementations must not mutate an existing snapshot in place. Lifecycle changes
 * replace a snapshot only when the stored status matches expectedStatus. Production
 * callers must execute snapshot writes and the corresponding BOQ aggregate update
 * in the same database transaction.
 */
public interface BoqRevisionSnapshotRepository {
    CompletionStage<Optional<BoqRevisionSnapshot>> find(String tenantId, UUID boqId, int revisionNumber);

    CompletionStage<List<BoqRevisionSnapshot>> findAllByBoqId(String tenantId, UUID boqId);

    /** Insert-only operation; duplicate (boqId, revisionNumber) must fail. */
    CompletionStage<Void> insert(String tenantId, BoqRevisionSnapshot snapshot);

    /**
     * Compare-and-set update. Must fail with a concurrency/conflict error if the
     * stored status is not expectedStatus or the row is absent.
     */
    CompletionStage<Void> replaceIfStatus(
            String tenantId,
            BoqRevisionSnapshot snapshot,
            BoqRevisionSnapshot.RevisionStatus expectedStatus
    );
}
