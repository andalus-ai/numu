package tech.kayys.numou.construction.spi.idempotency;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.domain.boq.BoqCalculationPolicy;
import tech.kayys.numou.construction.domain.boq.BoqRevisionSnapshot;
import tech.kayys.numou.construction.spi.outbox.TransactionalOutboxEvent;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Transaction-scoped capabilities for one command execution.
 *
 * <p>Every method must use the same database connection/transaction. Implementations
 * must not delegate to pool-level methods that acquire independent connections.</p>
 */
public interface TransactionalCommandContext {
    Uni<CommandIdempotencyStore.ReservationResult> reserve(
            CommandIdempotencyStore.Reservation reservation);

    Uni<Boolean> complete(String tenantId, String key, String requestFingerprint,
                          String responseJson, Instant completedAt);

    Uni<Optional<CommandIdempotencyStore.StoredOutcome>> find(String tenantId, String key);

    /** Inserts an event through this context's transaction-bound connection. */
    Uni<Void> appendOutboxEvent(TransactionalOutboxEvent event);

    BoqRevisionOperations boqRevisions();

    /** Transaction-bound BOQ operations; adapter must use this context's connection. */
    interface BoqRevisionOperations {
        Uni<BoqRevisionSnapshot> captureDraft(String tenantId, UUID boqId, UUID actorId,
                                               BoqCalculationPolicy policy, Instant now);
        Uni<BoqRevisionSnapshot> submitForReview(String tenantId, UUID boqId, int revisionNumber,
                                                  UUID actorId, Instant now);
        Uni<BoqRevisionSnapshot> approve(String tenantId, UUID boqId, int revisionNumber,
                                         UUID actorId, Instant now);
    }
}
