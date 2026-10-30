package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped persistence boundary for the consultancy evidence saga.
 *
 * <p>Every worker mutation is fenced by the lease token returned by
 * {@link #claimDue}. Implementations must never expose an unfenced save
 * operation: a worker whose lease expired must not overwrite a newer attempt.</p>
 */
public interface ConsultancyEvidenceOperationStore {
    /**
     * Creates the operation if its tenant/idempotency key is new. If the key
     * already exists, implementations return the existing operation only when
     * all immutable request fields match; otherwise they fail with an
     * idempotency conflict.
     */
    Uni<ConsultancyEvidenceOperation> createIfAbsent(ConsultancyEvidenceOperation operation);

    Uni<Optional<ConsultancyEvidenceOperation>> findByIdempotencyKey(
            String tenantId, String idempotencyKey);

    Uni<Optional<ConsultancyEvidenceOperation>> findById(String tenantId, UUID operationId);

    /**
     * Atomically claims due work. Expired leases are reclaimable. The returned
     * claim contains a fresh, unguessable token and must be supplied to every
     * subsequent state mutation.
     */
    Uni<List<ClaimedConsultancyEvidenceOperation>> claimDue(
            String tenantId, Instant now, Instant leaseUntil, int limit, String workerId);

    /** Returns false when the claim is stale, expired, or belongs to another worker. */
    Uni<Boolean> markLinked(ClaimedConsultancyEvidenceOperation claim, Instant now);

    /** Returns false when the claim is stale, expired, or belongs to another worker. */
    Uni<Boolean> markRetryableFailure(
            ClaimedConsultancyEvidenceOperation claim, String reason, Instant retryAt, Instant now);

    /** Returns false when the claim is stale, expired, or belongs to another worker. */
    Uni<Boolean> markTerminalFailure(
            ClaimedConsultancyEvidenceOperation claim, String reason, Instant now);

    record ClaimedConsultancyEvidenceOperation(
            ConsultancyEvidenceOperation operation,
            String workerId,
            UUID leaseToken,
            Instant leaseUntil) {
        public ClaimedConsultancyEvidenceOperation {
            if (operation == null || workerId == null || workerId.isBlank()
                    || leaseToken == null || leaseUntil == null) {
                throw new IllegalArgumentException("A complete evidence operation lease is required");
            }
        }
    }
}
