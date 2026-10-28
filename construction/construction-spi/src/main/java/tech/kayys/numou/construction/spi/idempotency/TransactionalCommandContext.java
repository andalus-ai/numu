package tech.kayys.numou.construction.spi.idempotency;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagement;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagementId;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyDeliverable;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyDeliverableId;
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

    /**
     * Transaction-bound consultancy writes. Adapters must use this context's
     * connection so the aggregate save and idempotency outcome commit atomically.
     * The default deliberately fails closed for adapters not yet upgraded.
     */
    default ConsultancyOperations consultancy() {
        throw new UnsupportedOperationException(
                "Transactional consultancy operations are not implemented by this adapter");
    }

    interface ConsultancyOperations {
        Uni<ConsultancyEngagement> saveEngagement(String tenantId, ConsultancyEngagement engagement);
        Uni<Optional<ConsultancyEngagement>> findEngagement(
                String tenantId, ConsultancyEngagementId engagementId);

        /** Fails closed for adapters that have not implemented deliverable persistence yet. */
        default Uni<ConsultancyDeliverable> saveDeliverable(
                String tenantId, ConsultancyDeliverable deliverable) {
            return Uni.createFrom().failure(new UnsupportedOperationException(
                    "Transactional consultancy deliverable inserts are not implemented by this adapter"));
        }

        /** Fails closed for adapters that have not implemented deliverable persistence yet. */
        default Uni<Optional<ConsultancyDeliverable>> findDeliverable(
                String tenantId, ConsultancyDeliverableId deliverableId) {
            return Uni.createFrom().failure(new UnsupportedOperationException(
                    "Transactional consultancy deliverable lookups are not implemented by this adapter"));
        }

        /** Updates a deliverable using a tenant-scoped lifecycle compare-and-set. */
        default Uni<ConsultancyDeliverable> updateDeliverable(
                String tenantId, ConsultancyDeliverable deliverable) {
            return Uni.createFrom().failure(new UnsupportedOperationException(
                    "Transactional consultancy deliverable updates are not implemented by this adapter"));
        }

        /** Updates an existing tenant-scoped engagement in the same command transaction. */
        default Uni<ConsultancyEngagement> updateEngagement(String tenantId, ConsultancyEngagement engagement) {
            return Uni.createFrom().failure(new UnsupportedOperationException(
                    "Transactional consultancy engagement updates are not implemented by this adapter"));
        }
    }

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
