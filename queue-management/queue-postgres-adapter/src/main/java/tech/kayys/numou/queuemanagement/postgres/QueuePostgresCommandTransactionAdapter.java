package tech.kayys.numou.queuemanagement.postgres;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.numou.queuemanagement.spi.QueueAuditRecord;
import tech.kayys.numou.queuemanagement.spi.QueueCommandContext;
import tech.kayys.numou.queuemanagement.spi.QueueCommandResult;
import tech.kayys.numou.queuemanagement.spi.QueueCommandTransactionPort;
import tech.kayys.numou.queuemanagement.spi.QueueEventRecord;
import tech.kayys.numou.queuemanagement.spi.QueueIdempotencyResultCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * PostgreSQL/JTA command boundary. The idempotency reservation, domain work, audit,
 * outbox and completed result all participate in one transaction. No intermediate
 * IN_PROGRESS state is committed or externally observable.
 */
@ApplicationScoped
public class QueuePostgresCommandTransactionAdapter implements QueueCommandTransactionPort {
    private final AgroalDataSource dataSource;
    private final Instance<QueueIdempotencyResultCodec<?>> codecs;

    @Inject
    public QueuePostgresCommandTransactionAdapter(
            AgroalDataSource dataSource,
            Instance<QueueIdempotencyResultCodec<?>> codecs) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.codecs = Objects.requireNonNull(codecs, "codecs must not be null");
    }

    @Override
    @Transactional
    public <T> T execute(QueueCommandContext context, String operationScope, String requestHash,
                         Supplier<QueueCommandResult<T>> work) {
        Objects.requireNonNull(context, "context must not be null");
        requireText(operationScope, "operationScope");
        requireText(requestHash, "requestHash");
        Objects.requireNonNull(work, "work must not be null");

        try (Connection connection = dataSource.getConnection()) {
            setTenant(connection, context);
            reserveKey(connection, context, operationScope, requestHash);
            StoredResult stored = readResult(connection, context, operationScope);
            if (stored.completed()) {
                if (!requestHash.equals(stored.requestHash())) {
                    throw new IdempotencyConflictException(
                            "Idempotency key was already used with a different request");
                }
                return decode(stored.resultType(), stored.payload());
            }
            if (!requestHash.equals(stored.requestHash())) {
                throw new IdempotencyConflictException(
                        "Idempotency key was concurrently reserved for a different request");
            }

            QueueCommandResult<T> result = Objects.requireNonNull(work.get(),
                    "Queue command work must return a result");
            QueueIdempotencyResultCodec<T> codec = codecFor(result.value());
            String payload = Objects.requireNonNull(codec.encode(result.value()),
                    "Idempotency codec returned a null payload");

            persistAudit(connection, result, context);
            persistEvents(connection, result, context);
            completeKey(connection, context, operationScope, requestHash, codec.resultType(), payload);
            return result.value();
        } catch (SQLException e) {
            throw new IllegalStateException("Queue command transaction failed", e);
        }
    }

    private static void setTenant(Connection connection, QueueCommandContext context) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('numou.tenant_id', ?, true)")) {
            statement.setString(1, context.tenantId().value().toString());
            statement.execute();
        }
    }

    private static void reserveKey(Connection connection, QueueCommandContext context,
                                   String scope, String hash) throws SQLException {
        String sql = "INSERT INTO queue_idempotency "
                + "(tenant_id, operation_scope, idempotency_key, request_hash, state) "
                + "VALUES (?, ?, ?, ?, 'IN_PROGRESS') ON CONFLICT "
                + "(tenant_id, operation_scope, idempotency_key) DO NOTHING";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, context.tenantId().value());
            statement.setString(2, scope);
            statement.setString(3, context.idempotencyKey());
            statement.setString(4, hash);
            statement.executeUpdate();
        }
    }

    private static StoredResult readResult(Connection connection, QueueCommandContext context,
                                           String scope) throws SQLException {
        String sql = "SELECT request_hash, state, result_type, result_payload::text AS result_payload "
                + "FROM queue_idempotency WHERE tenant_id = ? AND operation_scope = ? "
                + "AND idempotency_key = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, context.tenantId().value());
            statement.setString(2, scope);
            statement.setString(3, context.idempotencyKey());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Idempotency reservation disappeared");
                String state = rs.getString("state");
                return new StoredResult(rs.getString("request_hash"), "COMPLETED".equals(state),
                        rs.getString("result_type"), rs.getString("result_payload"));
            }
        }
    }

    private static void persistAudit(Connection connection, QueueCommandResult<?> result,
                                     QueueCommandContext context) throws SQLException {
        String sql = "INSERT INTO queue_audit "
                + "(audit_id, tenant_id, actor_id, action, resource_type, resource_id, correlation_id, occurred_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (QueueAuditRecord audit : result.auditRecords()) {
                requireTenant(context, audit.tenantId().value());
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, audit.tenantId().value());
                statement.setString(3, audit.actorId());
                statement.setString(4, audit.action());
                statement.setString(5, audit.resourceType());
                statement.setString(6, audit.resourceId());
                statement.setString(7, audit.correlationId());
                statement.setTimestamp(8, Timestamp.from(audit.occurredAt()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void persistEvents(Connection connection, QueueCommandResult<?> result,
                                      QueueCommandContext context) throws SQLException {
        String sql = "INSERT INTO queue_outbox "
                + "(event_id, tenant_id, event_type, schema_version, aggregate_type, aggregate_id, "
                + "correlation_id, occurred_at, payload) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (QueueEventRecord event : result.events()) {
                requireTenant(context, event.tenantId().value());
                statement.setObject(1, UUID.fromString(event.eventId()));
                statement.setObject(2, event.tenantId().value());
                statement.setString(3, event.eventType());
                statement.setInt(4, event.schemaVersion());
                statement.setString(5, event.aggregateType());
                statement.setString(6, event.aggregateId());
                statement.setString(7, event.correlationId());
                statement.setTimestamp(8, Timestamp.from(event.occurredAt()));
                statement.setString(9, event.payload());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void completeKey(Connection connection, QueueCommandContext context, String scope,
                                    String hash, String resultType, String payload) throws SQLException {
        String sql = "UPDATE queue_idempotency SET state = 'COMPLETED', result_type = ?, "
                + "result_payload = ?::jsonb, completed_at = ? WHERE tenant_id = ? "
                + "AND operation_scope = ? AND idempotency_key = ? AND request_hash = ? "
                + "AND state = 'IN_PROGRESS'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, resultType);
            statement.setString(2, payload);
            statement.setTimestamp(3, Timestamp.from(Instant.now()));
            statement.setObject(4, context.tenantId().value());
            statement.setString(5, scope);
            statement.setString(6, context.idempotencyKey());
            statement.setString(7, hash);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Unable to complete queue idempotency record");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> QueueIdempotencyResultCodec<T> codecFor(T value) {
        QueueIdempotencyResultCodec<?> match = null;
        for (QueueIdempotencyResultCodec<?> candidate : codecs) {
            if (candidate.javaType().equals(value.getClass())) {
                if (match != null) throw new IllegalStateException(
                        "Multiple queue idempotency codecs registered for " + value.getClass().getName());
                match = candidate;
            }
        }
        if (match == null) throw new IllegalStateException(
                "No queue idempotency result codec registered for " + value.getClass().getName());
        return (QueueIdempotencyResultCodec<T>) match;
    }

    @SuppressWarnings("unchecked")
    private <T> T decode(String resultType, String payload) {
        QueueIdempotencyResultCodec<?> match = null;
        for (QueueIdempotencyResultCodec<?> candidate : codecs) {
            if (candidate.resultType().equals(resultType)) {
                if (match != null) throw new IllegalStateException(
                        "Multiple codecs registered for result type " + resultType);
                match = candidate;
            }
        }
        if (match == null) throw new IllegalStateException(
                "No queue idempotency codec registered for stored result type " + resultType);
        return (T) match.decode(payload);
    }

    private static void requireTenant(QueueCommandContext context, UUID recordTenant) {
        if (!context.tenantId().value().equals(recordTenant)) {
            throw new IllegalArgumentException("Audit/outbox record tenant differs from command tenant");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    private record StoredResult(String requestHash, boolean completed, String resultType, String payload) { }

    /** Signals safe client-visible idempotency-key reuse conflict. */
    public static final class IdempotencyConflictException extends IllegalStateException {
        public IdempotencyConflictException(String message) { super(message); }
    }
}
