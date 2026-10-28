package tech.kayys.payment.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PostgreSQL relay store. Claims are atomic and acknowledgements are fenced by
 * a per-claim token, so an expired worker cannot overwrite a newer claim.
 */
@ApplicationScoped
public class PostgresPaymentOutboxRelayStore implements PaymentOutboxRelayStore {
    static final String CLAIM_BATCH = """
            WITH exhausted AS (
                UPDATE payment_operation_outbox
                   SET terminal = TRUE,
                       last_error = COALESCE(last_error, 'Outbox delivery attempts exhausted'),
                       worker_id = NULL, lease_token = NULL, locked_until = NULL
                 WHERE published_at IS NULL
                   AND terminal = FALSE
                   AND attempts >= max_attempts
                   AND (locked_until IS NULL OR locked_until <= ?)
                RETURNING event_id
            ),
            claimable AS (
                SELECT event_id
                  FROM payment_operation_outbox
                 WHERE published_at IS NULL
                   AND terminal = FALSE
                   AND attempts < max_attempts
                   AND next_attempt_at <= ?
                   AND (locked_until IS NULL OR locked_until <= ?)
                 ORDER BY next_attempt_at, occurred_at, event_id
                 FOR UPDATE SKIP LOCKED
                 LIMIT ?
            )
            UPDATE payment_operation_outbox AS event
               SET worker_id = ?,
                   lease_token = gen_random_uuid(),
                   locked_until = ?,
                   attempts = event.attempts + 1
              FROM claimable
             WHERE event.event_id = claimable.event_id
            RETURNING event.event_id, event.tenant_id, event.aggregate_type,
                      event.aggregate_id, event.event_type, event.payload,
                      event.occurred_at, event.attempts, event.max_attempts, event.lease_token
            """;

    static final String MARK_PUBLISHED = """
            UPDATE payment_operation_outbox
               SET published_at = ?, worker_id = NULL, lease_token = NULL,
                   locked_until = NULL, last_error = NULL
             WHERE event_id = ? AND published_at IS NULL
               AND terminal = FALSE AND lease_token = ?
            """;

    static final String MARK_FAILED = """
            UPDATE payment_operation_outbox
               SET next_attempt_at = ?, last_error = ?,
                   terminal = ?, worker_id = NULL, lease_token = NULL, locked_until = NULL
             WHERE event_id = ? AND published_at IS NULL
               AND terminal = FALSE AND lease_token = ?
            """;

    private final DataSource dataSource;

    @Inject
    public PostgresPaymentOutboxRelayStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<ClaimedEvent> claimBatch(String workerId, Instant now, Instant leaseUntil, int limit) {
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("workerId is required");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (!leaseUntil.isAfter(now)) throw new IllegalArgumentException("leaseUntil must be after now");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(CLAIM_BATCH)) {
            statement.setObject(1, toOffsetDateTime(now));
            statement.setObject(2, toOffsetDateTime(now));
            statement.setObject(3, toOffsetDateTime(now));
            statement.setInt(4, limit);
            statement.setString(5, workerId);
            statement.setObject(6, toOffsetDateTime(leaseUntil));
            try (ResultSet result = statement.executeQuery()) {
                List<ClaimedEvent> events = new ArrayList<>();
                while (result.next()) {
                    events.add(new ClaimedEvent(
                            result.getObject("event_id", UUID.class),
                            result.getString("tenant_id"),
                            result.getString("aggregate_type"),
                            result.getObject("aggregate_id", UUID.class),
                            result.getString("event_type"),
                            result.getString("payload"),
                            result.getTimestamp("occurred_at").toInstant(),
                            result.getInt("attempts"),
                            result.getInt("max_attempts"),
                            result.getObject("lease_token", UUID.class)));
                }
                return List.copyOf(events);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to claim payment outbox batch", exception);
        }
    }

    @Override
    public boolean markPublished(UUID eventId, UUID leaseToken, Instant publishedAt) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(leaseToken, "leaseToken");
        Objects.requireNonNull(publishedAt, "publishedAt");
        return executeUpdate(MARK_PUBLISHED, statement -> {
            statement.setObject(1, toOffsetDateTime(publishedAt));
            statement.setObject(2, eventId);
            statement.setObject(3, leaseToken);
        }) == 1;
    }

    @Override
    public boolean markFailed(UUID eventId, UUID leaseToken, Instant retryAt, String error, boolean terminal) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(leaseToken, "leaseToken");
        Objects.requireNonNull(retryAt, "retryAt");
        String suppliedError = error == null || error.isBlank() ? "Unknown delivery failure" : error;
        final String safeError = suppliedError.length() > 2000
                ? suppliedError.substring(0, 2000)
                : suppliedError;
        return executeUpdate(MARK_FAILED, statement -> {
            statement.setObject(1, toOffsetDateTime(retryAt));
            statement.setString(2, safeError);
            statement.setBoolean(3, terminal);
            statement.setObject(4, eventId);
            statement.setObject(5, leaseToken);
        }) == 1;
    }

    private int executeUpdate(String sql, StatementBinder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update payment outbox delivery state", exception);
        }
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
