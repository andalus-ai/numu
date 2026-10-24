package tech.kayys.numou.omnichannel.postgres;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.omnichannel.core.DeadLetterEvent;
import tech.kayys.numou.omnichannel.core.DeadLetterOperations;
import tech.kayys.numou.omnichannel.core.DeadLetterQuery;
import tech.kayys.numou.omnichannel.core.DeadLetterReplayResult;
import tech.kayys.numou.omnichannel.core.InboundEventKey;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL operator operations; replay state change and audit insert share one transaction. */
public final class PostgresDeadLetterOperations implements DeadLetterOperations {
    private final DataSource dataSource;

    public PostgresDeadLetterOperations(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public List<DeadLetterEvent> find(DeadLetterQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        StringBuilder sql = new StringBuilder("""
                SELECT tenant_id, provider_id, provider_account_id, external_event_id,
                       event_type, last_error_code, attempt_count, received_at, updated_at, correlation_id
                  FROM numou_omnichannel_inbound_event
                 WHERE tenant_id = ? AND state = 'DEAD_LETTER'
                """);
        if (query.providerId().isPresent()) sql.append(" AND provider_id = ?");
        if (query.errorCode().isPresent()) sql.append(" AND last_error_code = ?");
        if (query.updatedBefore().isPresent()) sql.append(" AND updated_at <= ?");
        sql.append(" AND attempt_count >= ? ORDER BY updated_at DESC LIMIT ?");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            statement.setString(index++, query.tenantId().toString());
            if (query.providerId().isPresent()) statement.setString(index++, query.providerId().orElseThrow());
            if (query.errorCode().isPresent()) statement.setString(index++, query.errorCode().orElseThrow());
            if (query.updatedBefore().isPresent()) {
                statement.setTimestamp(index++, Timestamp.from(query.updatedBefore().orElseThrow()));
            }
            statement.setInt(index++, query.minimumAttempts());
            statement.setInt(index, query.limit());
            try (ResultSet rs = statement.executeQuery()) {
                List<DeadLetterEvent> events = new ArrayList<>();
                while (rs.next()) {
                    events.add(new DeadLetterEvent(
                            new InboundEventKey(TenantId.of(rs.getString("tenant_id")),
                                    rs.getString("provider_id"), rs.getString("provider_account_id"),
                                    rs.getString("external_event_id")),
                            rs.getString("event_type"),
                            Optional.ofNullable(rs.getString("last_error_code")).orElse("UNKNOWN"),
                            rs.getInt("attempt_count"),
                            rs.getTimestamp("received_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant(),
                            Optional.ofNullable(rs.getString("correlation_id"))));
                }
                return List.copyOf(events);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to inspect tenant dead-letter events", exception);
        }
    }

    @Override
    public DeadLetterReplayResult replay(
            InboundEventKey key, String actorId, String reason, Instant requestedAt) {
        Objects.requireNonNull(key, "key must not be null");
        String actor = bounded(actorId, "actorId", 200);
        String safeReason = bounded(reason, "reason", 500);
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");

        String updateSql = """
                UPDATE numou_omnichannel_inbound_event
                   SET state = 'RETRYABLE_FAILURE', next_attempt_at = ?, lease_until = NULL,
                       lease_token = NULL, updated_at = ?
                 WHERE tenant_id = ? AND provider_id = ? AND provider_account_id = ?
                   AND external_event_id = ? AND state = 'DEAD_LETTER'
                RETURNING attempt_count
                """;
        String auditSql = """
                INSERT INTO numou_omnichannel_dead_letter_audit
                    (tenant_id, provider_id, provider_account_id, external_event_id,
                     actor_id, action, reason, attempt_count, requested_at)
                VALUES (?, ?, ?, ?, ?, 'REPLAY_REQUESTED_AND_QUEUED', ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int attempts;
                try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                    update.setTimestamp(1, Timestamp.from(requestedAt));
                    update.setTimestamp(2, Timestamp.from(requestedAt));
                    bindKey(update, 3, key);
                    try (ResultSet rs = update.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            return DeadLetterReplayResult.NOT_FOUND_OR_NOT_DEAD_LETTER;
                        }
                        attempts = rs.getInt("attempt_count");
                    }
                }
                try (PreparedStatement audit = connection.prepareStatement(auditSql)) {
                    bindKey(audit, 1, key);
                    audit.setString(5, actor);
                    audit.setString(6, safeReason);
                    audit.setInt(7, attempts);
                    audit.setTimestamp(8, Timestamp.from(requestedAt));
                    audit.executeUpdate();
                }
                connection.commit();
                return DeadLetterReplayResult.REQUEUED;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to queue audited dead-letter replay", exception);
        }
    }

    private static void bindKey(PreparedStatement statement, int start, InboundEventKey key) throws SQLException {
        statement.setString(start, key.tenantId().toString());
        statement.setString(start + 1, key.providerId());
        statement.setString(start + 2, key.providerAccountId());
        statement.setString(start + 3, key.externalEventId());
    }

    private static String bounded(String value, String field, int max) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + max + " characters");
        }
        return normalized;
    }
}
