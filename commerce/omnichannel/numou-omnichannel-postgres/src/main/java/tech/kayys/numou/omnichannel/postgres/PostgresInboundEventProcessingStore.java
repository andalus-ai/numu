package tech.kayys.numou.omnichannel.postgres;

import tech.kayys.numou.omnichannel.core.ChannelType;
import tech.kayys.numou.omnichannel.core.InboundEventKey;
import tech.kayys.numou.omnichannel.core.InboundEventProcessingStore;
import tech.kayys.numou.omnichannel.core.InboundWorkItem;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

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
import java.util.UUID;

/** JDBC lifecycle adapter using PostgreSQL row locks and lease-token CAS updates. */
public final class PostgresInboundEventProcessingStore implements InboundEventProcessingStore {

    private static final String CLAIM_SQL = """
            WITH candidate AS (
                SELECT tenant_id, provider_id, provider_account_id, external_event_id
                  FROM numou_omnichannel_inbound_event
                 WHERE attempt_count < ?
                   AND (
                       (state IN ('VERIFIED', 'RETRYABLE_FAILURE')
                           AND (next_attempt_at IS NULL OR next_attempt_at <= ?))
                       OR (state = 'PROCESSING' AND lease_until <= ?)
                   )
                 ORDER BY COALESCE(next_attempt_at, received_at), received_at
                 LIMIT 1
                 FOR UPDATE SKIP LOCKED
            )
            UPDATE numou_omnichannel_inbound_event e
               SET state = 'PROCESSING',
                   attempt_count = e.attempt_count + 1,
                   lease_until = ?,
                   lease_token = ?::uuid,
                   next_attempt_at = NULL,
                   updated_at = ?
              FROM candidate c
             WHERE e.tenant_id = c.tenant_id
               AND e.provider_id = c.provider_id
               AND e.provider_account_id = c.provider_account_id
               AND e.external_event_id = c.external_event_id
            RETURNING e.tenant_id, e.provider_id, e.provider_account_id, e.external_event_id,
                      e.event_type, e.payload_sha256, e.channel_type, e.branch_id,
                      e.correlation_id, e.received_at, e.attempt_count, e.lease_until,
                      e.lease_token
            """;

    private static final String MARK_PROCESSED_SQL = """
            UPDATE numou_omnichannel_inbound_event
               SET state = 'PROCESSED', processed_at = ?, lease_until = NULL, lease_token = NULL,
                   last_error_code = NULL, updated_at = ?
             WHERE tenant_id = ? AND provider_id = ? AND provider_account_id = ?
               AND external_event_id = ? AND state = 'PROCESSING' AND lease_token = ?::uuid
            """;

    private static final String RECORD_FAILURE_SQL = """
            UPDATE numou_omnichannel_inbound_event
               SET state = CASE WHEN attempt_count >= ? THEN 'DEAD_LETTER'
                                ELSE 'RETRYABLE_FAILURE' END,
                   next_attempt_at = CASE WHEN attempt_count >= ? THEN NULL ELSE ? END,
                   lease_until = NULL, lease_token = NULL, last_error_code = ?, updated_at = ?
             WHERE tenant_id = ? AND provider_id = ? AND provider_account_id = ?
               AND external_event_id = ? AND state = 'PROCESSING' AND lease_token = ?::uuid
            """;

    private static final String DEAD_LETTER_EXHAUSTED_SQL = """
            UPDATE numou_omnichannel_inbound_event
               SET state = 'DEAD_LETTER', next_attempt_at = NULL, lease_until = NULL,
                   lease_token = NULL, updated_at = ?
             WHERE attempt_count >= ?
               AND (
                   (state IN ('VERIFIED', 'RETRYABLE_FAILURE')
                       AND (next_attempt_at IS NULL OR next_attempt_at <= ?))
                   OR (state = 'PROCESSING' AND lease_until <= ?)
               )
            """;

    private static final String FIND_DEAD_LETTERS_SQL = """
            SELECT tenant_id, provider_id, provider_account_id, external_event_id,
                   event_type, payload_sha256, channel_type, branch_id,
                   correlation_id, received_at, attempt_count, updated_at, lease_until
              FROM numou_omnichannel_inbound_event
             WHERE state = 'DEAD_LETTER'
             ORDER BY updated_at DESC
             LIMIT ?
            """;

    private final DataSource dataSource;

    public PostgresInboundEventProcessingStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public Optional<InboundWorkItem> claimNext(Instant now, Instant leaseUntil, int maxAttempts) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(leaseUntil, "leaseUntil must not be null");
        if (!leaseUntil.isAfter(now)) throw new IllegalArgumentException("leaseUntil must be after now");
        requirePositive(maxAttempts, "maxAttempts");
        String leaseToken = UUID.randomUUID().toString();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(CLAIM_SQL)) {
            statement.setInt(1, maxAttempts);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setTimestamp(4, Timestamp.from(leaseUntil));
            statement.setString(5, leaseToken);
            statement.setTimestamp(6, Timestamp.from(now));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(readWorkItem(rs, leaseUntil, true)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to claim inbound event for processing", exception);
        }
    }

    @Override
    public boolean markProcessed(InboundWorkItem item, Instant completedAt) {
        requireLeased(item);
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        return updateKeyed(MARK_PROCESSED_SQL, statement -> {
            statement.setTimestamp(1, Timestamp.from(completedAt));
            statement.setTimestamp(2, Timestamp.from(completedAt));
            bindKey(statement, 3, item.key());
            statement.setString(7, item.leaseToken().orElseThrow());
        }) == 1;
    }

    @Override
    public boolean recordFailure(InboundWorkItem item, String errorCode, Instant retryAt, int maxAttempts) {
        requireLeased(item);
        Objects.requireNonNull(retryAt, "retryAt must not be null");
        String safeCode = sanitizeErrorCode(errorCode);
        requirePositive(maxAttempts, "maxAttempts");
        return updateKeyed(RECORD_FAILURE_SQL, statement -> {
            statement.setInt(1, maxAttempts);
            statement.setInt(2, maxAttempts);
            statement.setTimestamp(3, Timestamp.from(retryAt));
            statement.setString(4, safeCode);
            statement.setTimestamp(5, Timestamp.from(Instant.now()));
            bindKey(statement, 6, item.key());
            statement.setString(10, item.leaseToken().orElseThrow());
        }) == 1;
    }

    @Override
    public int deadLetterExhausted(Instant now, int maxAttempts) {
        Objects.requireNonNull(now, "now must not be null");
        requirePositive(maxAttempts, "maxAttempts");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DEAD_LETTER_EXHAUSTED_SQL)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setInt(2, maxAttempts);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setTimestamp(4, Timestamp.from(now));
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to dead-letter exhausted inbox events", exception);
        }
    }

    @Override
    public List<InboundWorkItem> findDeadLetters(int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_DEAD_LETTERS_SQL)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<InboundWorkItem> items = new ArrayList<>();
                while (rs.next()) items.add(readWorkItem(rs, rs.getTimestamp("updated_at").toInstant(), false));
                return List.copyOf(items);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query dead-letter inbox events", exception);
        }
    }

    private int updateKeyed(String sql, SqlBinder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update inbound event lifecycle", exception);
        }
    }

    private static InboundWorkItem readWorkItem(ResultSet rs, Instant fallbackLease, boolean leased) throws SQLException {
        InboundEventKey key = new InboundEventKey(
                TenantId.of(rs.getString("tenant_id")),
                rs.getString("provider_id"),
                rs.getString("provider_account_id"),
                rs.getString("external_event_id"));
        Timestamp lease = rs.getTimestamp("lease_until");
        Timestamp received = rs.getTimestamp("received_at");
        String token = leased ? rs.getString("lease_token") : null;
        return new InboundWorkItem(
                key,
                rs.getString("event_type"),
                rs.getString("payload_sha256"),
                ChannelType.valueOf(rs.getString("channel_type")),
                Optional.ofNullable(rs.getString("branch_id")),
                rs.getString("correlation_id"),
                received.toInstant(),
                rs.getInt("attempt_count"),
                lease == null ? fallbackLease : lease.toInstant(),
                Optional.ofNullable(token));
    }

    private static void bindKey(PreparedStatement statement, int start, InboundEventKey key) throws SQLException {
        statement.setString(start, key.tenantId().toString());
        statement.setString(start + 1, key.providerId());
        statement.setString(start + 2, key.providerAccountId());
        statement.setString(start + 3, key.externalEventId());
    }

    private static void requireLeased(InboundWorkItem item) {
        Objects.requireNonNull(item, "item must not be null");
        if (item.leaseToken().isEmpty()) {
            throw new IllegalArgumentException("a currently leased work item is required");
        }
    }

    private static String sanitizeErrorCode(String errorCode) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        String safe = errorCode.trim();
        if (safe.isEmpty() || safe.length() > 100 || !safe.matches("[A-Za-z0-9_.:-]+")) {
            throw new IllegalArgumentException("errorCode must be a sanitized code of at most 100 characters");
        }
        return safe;
    }

    private static void requirePositive(int value, String field) {
        if (value < 1) throw new IllegalArgumentException(field + " must be positive");
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
