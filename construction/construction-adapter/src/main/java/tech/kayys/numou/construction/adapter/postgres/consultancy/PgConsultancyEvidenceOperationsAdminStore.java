package tech.kayys.numou.construction.adapter.postgres.consultancy;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.construction.application.consultancy.ConsultancyEvidenceOperation;
import tech.kayys.numou.construction.application.consultancy.ConsultancyEvidenceOperationsAdminStore;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL implementation; recovery and its audit record commit atomically. */
@ApplicationScoped
public class PgConsultancyEvidenceOperationsAdminStore implements ConsultancyEvidenceOperationsAdminStore {
    private static final String COLUMNS = """
        operation_id, tenant_id, idempotency_key, deliverable_id, document_id, document_version_id,
        relationship, requested_by, requesting_organization_id, status, attempts, last_failure,
        created_at, updated_at
        """;
    @Inject PgPool pool;

    @Override
    public Uni<List<ConsultancyEvidenceOperation>> list(
            String tenantId, Optional<ConsultancyEvidenceOperation.Status> status, int limit, int offset) {
        String sql = "SELECT " + COLUMNS + """
             FROM construction_consultancy_evidence_operation
            WHERE tenant_id = $1 AND ($2::varchar IS NULL OR status = $2)
            ORDER BY updated_at DESC, operation_id
            LIMIT $3 OFFSET $4
            """;
        return pool.preparedQuery(sql).execute(Tuple.of(tenantId,
                status.map(Enum::name).orElse(null), limit, offset))
                .map(rows -> {
                    List<ConsultancyEvidenceOperation> result = new ArrayList<>();
                    for (Row row : rows) result.add(map(row));
                    return List.copyOf(result);
                });
    }

    @Override
    public Uni<ConsultancyEvidenceOperation> recoverTerminalFailure(RecoveryRequest request) {
        return pool.withTransaction(connection -> recover(connection, request));
    }

    private Uni<ConsultancyEvidenceOperation> recover(SqlConnection connection, RecoveryRequest request) {
        String find = "SELECT " + COLUMNS + """
             FROM construction_consultancy_evidence_operation
            WHERE tenant_id = $1 AND operation_id = $2
            FOR UPDATE
            """;
        return connection.preparedQuery(find)
                .execute(Tuple.of(request.tenantId(), request.operationId()))
                .chain(rows -> {
                    if (!rows.iterator().hasNext()) {
                        return Uni.createFrom().failure(new EvidenceOperationNotFoundException());
                    }
                    ConsultancyEvidenceOperation current = map(rows.iterator().next());
                    if (current.status() != ConsultancyEvidenceOperation.Status.TERMINAL_FAILURE) {
                        return findPriorRecovery(connection, request)
                                .chain(prior -> prior.<Uni<ConsultancyEvidenceOperation>>map(existing -> {
                                    if (!existing.operationId().equals(request.operationId())) {
                                        return Uni.createFrom().failure(new EvidenceRecoveryIdempotencyConflictException());
                                    }
                                    return Uni.createFrom().item(current);
                                }).orElseGet(() -> Uni.createFrom().failure(
                                        new EvidenceOperationNotRecoverableException(current.status()))));
                    }
                    return appendAudit(connection, request)
                            .chain(inserted -> {
                                if (!inserted) {
                                    return findPriorRecovery(connection, request)
                                            .chain(prior -> prior.<Uni<ConsultancyEvidenceOperation>>map(existing -> {
                                                if (!existing.operationId().equals(request.operationId())) {
                                                    return Uni.createFrom().failure(new EvidenceRecoveryIdempotencyConflictException());
                                                }
                                                return Uni.createFrom().item(current);
                                            }).orElseGet(() -> Uni.createFrom().failure(
                                                    new EvidenceRecoveryIdempotencyConflictException())));
                                }
                                String update = """
                                    UPDATE construction_consultancy_evidence_operation
                                       SET status = 'PENDING', available_at = $3,
                                           worker_id = NULL, lease_token = NULL, locked_until = NULL,
                                           updated_at = $3
                                     WHERE tenant_id = $1 AND operation_id = $2
                                       AND status = 'TERMINAL_FAILURE'
                                    RETURNING
                                    """ + COLUMNS;
                                return connection.preparedQuery(update)
                                        .execute(Tuple.of(request.tenantId(), request.operationId(), utc(request.occurredAt())))
                                        .map(updated -> {
                                            if (!updated.iterator().hasNext()) {
                                                throw new IllegalStateException("Evidence operation changed during locked recovery");
                                            }
                                            return map(updated.iterator().next());
                                        });
                            });
                });
    }

    private Uni<Boolean> appendAudit(SqlConnection connection, RecoveryRequest request) {
        String sql = """
            INSERT INTO construction_consultancy_evidence_operation_recovery_audit
                (tenant_id, audit_id, operation_id, actor_id, reason, idempotency_key,
                 previous_status, resulting_status, occurred_at)
            VALUES ($1, $2, $3, $4, $5, $6, 'TERMINAL_FAILURE', 'PENDING', $7)
            ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
            RETURNING audit_id
            """;
        return connection.preparedQuery(sql).execute(Tuple.of(request.tenantId(), UUID.randomUUID(),
                request.operationId(), request.actorId(), request.reason(), request.idempotencyKey(),
                utc(request.occurredAt()))).map(rows -> rows.iterator().hasNext());
    }

    private Uni<Optional<ConsultancyEvidenceOperation>> findPriorRecovery(
            SqlConnection connection, RecoveryRequest request) {
        String sql = """
            SELECT operation_id FROM construction_consultancy_evidence_operation_recovery_audit
             WHERE tenant_id = $1 AND idempotency_key = $2 AND actor_id = $3 AND reason = $4
            """;
        return connection.preparedQuery(sql).execute(Tuple.of(
                request.tenantId(), request.idempotencyKey(), request.actorId(), request.reason()))
                .map(rows -> rows.iterator().hasNext()
                        ? Optional.ofNullable(findOperationFromRow(rows.iterator().next(), request))
                        : Optional.empty());
    }

    // The audit row is authoritative for idempotency; operation was already locked/read in this transaction.
    private Optional<ConsultancyEvidenceOperation> findOperationFromRow(Row row, RecoveryRequest request) {
        UUID id = row.getUUID("operation_id");
        if (!id.equals(request.operationId())) return Optional.empty();
        return Optional.of(new ConsultancyEvidenceOperation(
                id, request.tenantId(), "", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "audit-placeholder", request.actorId(), UUID.randomUUID(),
                ConsultancyEvidenceOperation.Status.PENDING, 0, null, request.occurredAt(), request.occurredAt()));
    }

    private static ConsultancyEvidenceOperation map(Row row) {
        return new ConsultancyEvidenceOperation(row.getUUID("operation_id"), row.getString("tenant_id"),
                row.getString("idempotency_key"), row.getUUID("deliverable_id"), row.getUUID("document_id"),
                row.getUUID("document_version_id"), row.getString("relationship"), row.getUUID("requested_by"),
                row.getUUID("requesting_organization_id"), ConsultancyEvidenceOperation.Status.valueOf(row.getString("status")),
                row.getInteger("attempts"), row.getString("last_failure"),
                row.getOffsetDateTime("created_at").toInstant(), row.getOffsetDateTime("updated_at").toInstant());
    }
    private static OffsetDateTime utc(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }

    public static final class EvidenceOperationNotFoundException extends RuntimeException {}
    public static final class EvidenceRecoveryIdempotencyConflictException extends RuntimeException {}
    public static final class EvidenceOperationNotRecoverableException extends RuntimeException {
        public EvidenceOperationNotRecoverableException(ConsultancyEvidenceOperation.Status status) {
            super("Only terminal failures can be recovered; current status is " + status);
        }
    }
}
