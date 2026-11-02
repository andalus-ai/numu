package tech.kayys.numou.construction.adapter.postgres.consultancy;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.construction.application.consultancy.ConsultancyEvidenceOperation;
import tech.kayys.numou.construction.application.consultancy.ConsultancyEvidenceOperationStore;
import tech.kayys.numou.construction.application.consultancy.ConsultancyEvidenceOperationStore.ClaimedConsultancyEvidenceOperation;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL persistence for durable consultancy evidence attachment operations. */
@ApplicationScoped
public class PgConsultancyEvidenceOperationStore implements ConsultancyEvidenceOperationStore {
    private static final String COLUMNS = """
            tenant_id, operation_id, idempotency_key, deliverable_id, document_id,
            document_version_id, relationship, requested_by, requesting_organization_id, status, attempts,
            last_failure, created_at, updated_at
            """;

    private static final String INSERT = """
            INSERT INTO construction_consultancy_evidence_operation
                (tenant_id, operation_id, idempotency_key, deliverable_id, document_id,
                 document_version_id, relationship, requested_by, requesting_organization_id, status, attempts,
                 last_failure, available_at, created_at, updated_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, 'PENDING', 0, NULL, $10, $11, $11)
            ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
            RETURNING
            """ + COLUMNS;

    private static final String FIND_BY_KEY = "SELECT " + COLUMNS + """
              FROM construction_consultancy_evidence_operation
             WHERE tenant_id = $1 AND idempotency_key = $2
            """;

    private static final String FIND_BY_ID = "SELECT " + COLUMNS + """
              FROM construction_consultancy_evidence_operation
             WHERE tenant_id = $1 AND operation_id = $2
            """;

    private static final String CLAIM_DUE = """
            WITH claimable AS (
                SELECT tenant_id, operation_id
                  FROM construction_consultancy_evidence_operation
                 WHERE tenant_id = $1
                   AND status IN ('PENDING', 'RETRYABLE_FAILURE')
                   AND available_at <= $2
                   AND (lease_token IS NULL OR locked_until <= $2)
                 ORDER BY available_at, created_at, operation_id
                 FOR UPDATE SKIP LOCKED
                 LIMIT $4
            )
            UPDATE construction_consultancy_evidence_operation AS operation
               SET worker_id = $5,
                   lease_token = gen_random_uuid(),
                   locked_until = $3,
                   attempts = operation.attempts + 1,
                   updated_at = $2
              FROM claimable
             WHERE operation.tenant_id = claimable.tenant_id
               AND operation.operation_id = claimable.operation_id
            RETURNING operation.tenant_id, operation.operation_id, operation.idempotency_key,
                      operation.deliverable_id, operation.document_id, operation.document_version_id,
                      operation.relationship, operation.requested_by, operation.requesting_organization_id, operation.status,
                      operation.attempts, operation.last_failure, operation.created_at,
                      operation.updated_at, operation.worker_id, operation.lease_token,
                      operation.locked_until
            """;

    private static final String RENEW_LEASE = """
            UPDATE construction_consultancy_evidence_operation
               SET locked_until = $4, updated_at = $3
             WHERE tenant_id = $1 AND operation_id = $2 AND lease_token = $5
               AND locked_until > $3 AND status IN ('PENDING', 'RETRYABLE_FAILURE')
            RETURNING worker_id, lease_token, locked_until
            """;

    private static final String MARK_LINKED = """
            UPDATE construction_consultancy_evidence_operation
               SET status = 'LINKED', last_failure = NULL,
                   worker_id = NULL, lease_token = NULL, locked_until = NULL, updated_at = $4
             WHERE tenant_id = $1 AND operation_id = $2 AND lease_token = $3
               AND locked_until > $4 AND status IN ('PENDING', 'RETRYABLE_FAILURE')
            """;

    private static final String MARK_RETRYABLE = """
            UPDATE construction_consultancy_evidence_operation
               SET status = 'RETRYABLE_FAILURE', last_failure = $4, available_at = $5,
                   worker_id = NULL, lease_token = NULL, locked_until = NULL, updated_at = $6
             WHERE tenant_id = $1 AND operation_id = $2 AND lease_token = $3
               AND locked_until > $6 AND status IN ('PENDING', 'RETRYABLE_FAILURE')
            """;

    private static final String MARK_TERMINAL = """
            UPDATE construction_consultancy_evidence_operation
               SET status = 'TERMINAL_FAILURE', last_failure = $4,
                   worker_id = NULL, lease_token = NULL, locked_until = NULL, updated_at = $5
             WHERE tenant_id = $1 AND operation_id = $2 AND lease_token = $3
               AND locked_until > $5 AND status IN ('PENDING', 'RETRYABLE_FAILURE')
            """;

    @Inject
    PgPool pool;

    @Override
    public Uni<ConsultancyEvidenceOperation> createIfAbsent(ConsultancyEvidenceOperation operation) {
        Objects.requireNonNull(operation, "operation");
        return pool.preparedQuery(INSERT).execute(Tuple.of(
                operation.tenantId(), operation.operationId(), operation.idempotencyKey(),
                operation.deliverableId(), operation.documentId(), operation.documentVersionId(),
                operation.relationship(), operation.requestedBy(), operation.requestingOrganizationId(),
                toOffsetDateTime(operation.createdAt()), toOffsetDateTime(operation.createdAt())))
                .map(rows -> rows.iterator().hasNext() ? map(rows.iterator().next()) : null)
                .chain(inserted -> {
                    if (inserted != null) return Uni.createFrom().item(inserted);
                    return findByIdempotencyKey(operation.tenantId(), operation.idempotencyKey())
                            .map(existing -> existing.orElseThrow(() ->
                                    new IllegalStateException("Idempotent evidence operation disappeared after conflict")))
                            .map(existing -> {
                                if (!sameRequest(existing, operation)) {
                                    throw new EvidenceIdempotencyConflictException(
                                            "Idempotency key is already used for a different evidence attachment request");
                                }
                                return existing;
                            });
                });
    }

    @Override
    public Uni<Optional<ConsultancyEvidenceOperation>> findByIdempotencyKey(String tenantId, String key) {
        requireText(tenantId, "tenantId");
        requireText(key, "idempotencyKey");
        return pool.preparedQuery(FIND_BY_KEY).execute(Tuple.of(tenantId.trim(), key.trim()))
                .map(rows -> rows.iterator().hasNext()
                        ? Optional.of(map(rows.iterator().next())) : Optional.empty());
    }

    @Override
    public Uni<Optional<ConsultancyEvidenceOperation>> findById(String tenantId, UUID operationId) {
        requireText(tenantId, "tenantId");
        Objects.requireNonNull(operationId, "operationId");
        return pool.preparedQuery(FIND_BY_ID).execute(Tuple.of(tenantId.trim(), operationId))
                .map(rows -> rows.iterator().hasNext()
                        ? Optional.of(map(rows.iterator().next())) : Optional.empty());
    }

    @Override
    public Uni<List<ClaimedConsultancyEvidenceOperation>> claimDue(
            String tenantId, Instant now, Instant leaseUntil, int limit, String workerId) {
        requireText(tenantId, "tenantId");
        requireText(workerId, "workerId");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (!leaseUntil.isAfter(now)) throw new IllegalArgumentException("leaseUntil must be after now");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        return pool.preparedQuery(CLAIM_DUE).execute(Tuple.of(
                tenantId.trim(), toOffsetDateTime(now), toOffsetDateTime(leaseUntil), limit, workerId.trim()))
                .map(rows -> {
                    List<ClaimedConsultancyEvidenceOperation> claims = new ArrayList<>();
                    for (Row row : rows) {
                        claims.add(new ClaimedConsultancyEvidenceOperation(
                                map(row), row.getString("worker_id"), row.getUUID("lease_token"),
                                row.getOffsetDateTime("locked_until").toInstant()));
                    }
                    return List.copyOf(claims);
                });
    }

    @Override
    public Uni<Optional<ClaimedConsultancyEvidenceOperation>> renewLease(
            ClaimedConsultancyEvidenceOperation claim, Instant now, Instant leaseUntil) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (!leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("leaseUntil must be after now");
        }
        return pool.preparedQuery(RENEW_LEASE).execute(Tuple.of(
                claim.operation().tenantId(), claim.operation().operationId(),
                toOffsetDateTime(now), toOffsetDateTime(leaseUntil), claim.leaseToken()))
                .map(rows -> {
                    if (!rows.iterator().hasNext()) {
                        return Optional.empty();
                    }
                    Row row = rows.iterator().next();
                    return Optional.of(new ClaimedConsultancyEvidenceOperation(
                            claim.operation(), row.getString("worker_id"),
                            row.getUUID("lease_token"),
                            row.getOffsetDateTime("locked_until").toInstant()));
                });
    }

    @Override
    public Uni<Boolean> markLinked(ClaimedConsultancyEvidenceOperation claim, Instant now) {
        Objects.requireNonNull(claim, "claim");
        return pool.preparedQuery(MARK_LINKED).execute(Tuple.of(
                claim.operation().tenantId(), claim.operation().operationId(), claim.leaseToken(),
                toOffsetDateTime(Objects.requireNonNull(now, "now"))))
                .map(rows -> rows.rowCount() == 1);
    }

    @Override
    public Uni<Boolean> markRetryableFailure(
            ClaimedConsultancyEvidenceOperation claim, String reason, Instant retryAt, Instant now) {
        Objects.requireNonNull(claim, "claim");
        String safeReason = requireText(reason, "reason");
        if (safeReason.length() > 2000) safeReason = safeReason.substring(0, 2000);
        Objects.requireNonNull(retryAt, "retryAt");
        Objects.requireNonNull(now, "now");
        if (retryAt.isBefore(now)) throw new IllegalArgumentException("retryAt cannot precede now");
        return pool.preparedQuery(MARK_RETRYABLE).execute(Tuple.of(
                claim.operation().tenantId(), claim.operation().operationId(), claim.leaseToken(),
                safeReason, toOffsetDateTime(retryAt), toOffsetDateTime(now)))
                .map(rows -> rows.rowCount() == 1);
    }

    @Override
    public Uni<Boolean> markTerminalFailure(
            ClaimedConsultancyEvidenceOperation claim, String reason, Instant now) {
        Objects.requireNonNull(claim, "claim");
        String safeReason = requireText(reason, "reason");
        if (safeReason.length() > 2000) safeReason = safeReason.substring(0, 2000);
        return pool.preparedQuery(MARK_TERMINAL).execute(Tuple.of(
                claim.operation().tenantId(), claim.operation().operationId(), claim.leaseToken(),
                safeReason, toOffsetDateTime(Objects.requireNonNull(now, "now"))))
                .map(rows -> rows.rowCount() == 1);
    }

    private static boolean sameRequest(
            ConsultancyEvidenceOperation existing, ConsultancyEvidenceOperation requested) {
        return existing.deliverableId().equals(requested.deliverableId())
                && existing.documentId().equals(requested.documentId())
                && existing.documentVersionId().equals(requested.documentVersionId())
                && existing.relationship().equals(requested.relationship())
                && existing.requestedBy().equals(requested.requestedBy())
                && existing.requestingOrganizationId().equals(requested.requestingOrganizationId());
    }

    private static ConsultancyEvidenceOperation map(Row row) {
        return new ConsultancyEvidenceOperation(
                row.getUUID("operation_id"), row.getString("tenant_id"), row.getString("idempotency_key"),
                row.getUUID("deliverable_id"), row.getUUID("document_id"), row.getUUID("document_version_id"),
                row.getString("relationship"), row.getUUID("requested_by"), row.getUUID("requesting_organization_id"),
                ConsultancyEvidenceOperation.Status.valueOf(row.getString("status")),
                row.getInteger("attempts"), row.getString("last_failure"),
                row.getOffsetDateTime("created_at").toInstant(), row.getOffsetDateTime("updated_at").toInstant());
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    public static final class EvidenceIdempotencyConflictException extends RuntimeException {
        public EvidenceIdempotencyConflictException(String message) { super(message); }
    }
}
