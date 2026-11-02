package tech.kayys.numou.security.vault.postgres;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import tech.kayys.numou.security.vault.spi.VaultAuditOutboxEvent;
import tech.kayys.numou.security.vault.spi.VaultAuditOutboxPort;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** PostgreSQL lease/ack adapter. Claims use SKIP LOCKED for competing workers. */
public final class PostgresVaultAuditOutboxAdapter implements VaultAuditOutboxPort {
    private final DataSource dataSource;

    public PostgresVaultAuditOutboxAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    @Override
    public Uni<List<VaultAuditOutboxEvent>> claimDue(String workerId, Instant now,
                                                      Instant leaseUntil, int limit) {
        if (workerId == null || workerId.isBlank() || workerId.length() > 160)
            return Uni.createFrom().failure(new IllegalArgumentException("invalid workerId"));
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (!leaseUntil.isAfter(now) || limit < 1 || limit > 500)
            return Uni.createFrom().failure(new IllegalArgumentException("invalid lease or limit"));
        return Uni.createFrom().item(() -> claimBlocking(workerId, now, leaseUntil, limit))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private List<VaultAuditOutboxEvent> claimBlocking(String workerId, Instant now,
                                                        Instant leaseUntil, int limit) {
        String sql = """
            WITH due AS (
                SELECT event_id FROM vault_audit_outbox
                WHERE published_at IS NULL AND dead_lettered_at IS NULL
                  AND next_attempt_at <= ?
                  AND (lease_until IS NULL OR lease_until <= ?)
                ORDER BY next_attempt_at, occurred_at, event_id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            )
            UPDATE vault_audit_outbox o
            SET lease_owner = ?, lease_until = ?, attempts = o.attempts + 1
            FROM due
            WHERE o.event_id = due.event_id
            RETURNING o.event_id, o.tenant_id, o.vault_id, o.secret_id,
                      o.secret_version, o.actor, o.event_type, o.details,
                      o.occurred_at, o.attempts
            """;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement exhausted = connection.prepareStatement("""
                    UPDATE vault_audit_outbox
                    SET dead_lettered_at = ?, lease_owner = NULL, lease_until = NULL,
                        last_error = COALESCE(last_error, 'LeaseExpiredAfterMaxAttempts')
                    WHERE published_at IS NULL AND dead_lettered_at IS NULL
                      AND lease_until <= ? AND attempts >= 12
                    """)) {
                exhausted.setTimestamp(1, Timestamp.from(now));
                exhausted.setTimestamp(2, Timestamp.from(now));
                exhausted.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setTimestamp(2, Timestamp.from(now));
                statement.setInt(3, limit);
                statement.setString(4, workerId);
                statement.setTimestamp(5, Timestamp.from(leaseUntil));
                List<VaultAuditOutboxEvent> events = new ArrayList<>();
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        events.add(new VaultAuditOutboxEvent(
                                result.getObject("event_id", UUID.class),
                                result.getObject("tenant_id", UUID.class),
                                result.getObject("vault_id", UUID.class),
                                result.getObject("secret_id", UUID.class),
                                result.getInt("secret_version"), result.getString("actor"),
                                result.getString("event_type"), result.getString("details"),
                                result.getTimestamp("occurred_at").toInstant(), result.getInt("attempts")));
                    }
                }
                connection.commit();
                return List.copyOf(events);
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new VaultPersistenceException("Unable to claim vault audit events", failure);
            }
        } catch (SQLException failure) {
            throw new VaultPersistenceException("Unable to open vault outbox transaction", failure);
        }
    }

    @Override
    public Uni<Void> markPublished(UUID eventId, String workerId, Instant publishedAt) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(publishedAt, "publishedAt");
        return updateLease(eventId, workerId, """
            UPDATE vault_audit_outbox
            SET published_at = ?, lease_owner = NULL, lease_until = NULL, last_error = NULL
            WHERE event_id = ? AND lease_owner = ? AND published_at IS NULL
              AND dead_lettered_at IS NULL
            """, statement -> {
                statement.setTimestamp(1, Timestamp.from(publishedAt));
                statement.setObject(2, eventId);
                statement.setString(3, workerId);
            }, "Unable to acknowledge vault audit event");
    }

    @Override
    public Uni<Void> reschedule(UUID eventId, String workerId, Instant nextAttemptAt,
                                String safeError, boolean deadLetter) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        String error = safeError == null ? "PublishFailure" : safeError;
        if (error.length() > 200) error = error.substring(0, 200);
        final String boundedError = error;
        return updateLease(eventId, workerId, """
            UPDATE vault_audit_outbox
            SET next_attempt_at = ?, last_error = ?,
                dead_lettered_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,
                lease_owner = NULL, lease_until = NULL
            WHERE event_id = ? AND lease_owner = ? AND published_at IS NULL
              AND dead_lettered_at IS NULL
            """, statement -> {
                statement.setTimestamp(1, Timestamp.from(nextAttemptAt));
                statement.setString(2, boundedError);
                statement.setBoolean(3, deadLetter);
                statement.setObject(4, eventId);
                statement.setString(5, workerId);
            }, "Unable to reschedule vault audit event");
    }

    private Uni<Void> updateLease(UUID eventId, String workerId, String sql,
                                  Binder binder, String message) {
        if (workerId == null || workerId.isBlank() || workerId.length() > 160)
            return Uni.createFrom().failure(new IllegalArgumentException("invalid workerId"));
        return Uni.createFrom().item(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                if (statement.executeUpdate() != 1)
                    throw new IllegalStateException("vault audit lease is no longer owned by worker");
                return (Void) null;
            } catch (SQLException failure) {
                throw new VaultPersistenceException(message, failure);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
