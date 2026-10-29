package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable persistence port for the Construction-owned evidence attachment saga.
 * Implementations must enforce uniqueness of (tenantId, idempotencyKey) and
 * atomically claim due operations so concurrent workers cannot process the same
 * operation at once. A database-backed implementation and migration are required
 * before this workflow can be exposed in production.
 */
public interface ConsultancyEvidenceOperationStore {
    Uni<ConsultancyEvidenceOperation> createIfAbsent(ConsultancyEvidenceOperation operation);

    Uni<Optional<ConsultancyEvidenceOperation>> findByIdempotencyKey(
            String tenantId, String idempotencyKey);

    Uni<Optional<ConsultancyEvidenceOperation>> findById(String tenantId, UUID operationId);

    Uni<ConsultancyEvidenceOperation> save(ConsultancyEvidenceOperation operation);

    Uni<List<ConsultancyEvidenceOperation>> claimPending(
            String tenantId, Instant now, int limit, UUID workerId);
}
