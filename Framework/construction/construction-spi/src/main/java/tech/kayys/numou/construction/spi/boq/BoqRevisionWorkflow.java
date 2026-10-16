package tech.kayys.syirkah.construction.spi.boq;

import tech.kayys.syirkah.construction.domain.boq.BoqCalculationPolicy;
import tech.kayys.syirkah.construction.domain.boq.BoqRevisionSnapshot;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Transactional port for BOQ revision operations.
 *
 * Every operation carries an explicit tenant ID resolved from trusted server-side
 * context. Implementations must scope all reads and writes (including snapshots,
 * revision counters, idempotency, and outbox records) to that tenant. Never derive
 * tenant identity from a client command payload.
 *
 * Implementations must persist the snapshot and corresponding BOQ aggregate
 * transition atomically (or via an equivalent transactional-outbox strategy).
 * They must enforce revision/status compare-and-set semantics to prevent two
 * concurrent users from approving or submitting stale revisions.
 */
public interface BoqRevisionWorkflow {
    CompletionStage<BoqRevisionSnapshot> captureDraft(
            String tenantId,
            UUID boqId,
            UUID actorId,
            BoqCalculationPolicy calculationPolicy,
            Instant now
    );

    CompletionStage<BoqRevisionSnapshot> submitForReview(
            String tenantId,
            UUID boqId,
            int revisionNumber,
            UUID actorId,
            Instant now
    );

    CompletionStage<BoqRevisionSnapshot> approve(
            String tenantId,
            UUID boqId,
            int revisionNumber,
            UUID actorId,
            Instant now
    );
}
