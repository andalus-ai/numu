package tech.kayys.numou.feedback.adapter.postgres;

import tech.kayys.numou.feedback.application.outbox.FeedbackOutboxMessage;
import tech.kayys.numou.feedback.application.outbox.FeedbackOutboxStore;

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
import java.util.UUID;

/** JDBC lease-based outbox store. Claiming is safe across concurrent workers via SKIP LOCKED. */
public final class PostgresFeedbackOutboxStore implements FeedbackOutboxStore {
    private final DataSource dataSource;

    public PostgresFeedbackOutboxStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public List<FeedbackOutboxMessage> claimPending(Instant now, Instant leaseUntil, int limit) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (!leaseUntil.isAfter(now)) throw new IllegalArgumentException("leaseUntil must be after now");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be 1..500");

        String select = """
                SELECT event_id, tenant_id, aggregate_id, event_type, event_version,
                       payload::text AS payload, occurred_at, attempts
                  FROM feedback_outbox
                 WHERE published_at IS NULL
                   AND dead_lettered_at IS NULL
                   AND next_attempt_at <= ?
                   AND (lease_until IS NULL OR lease_until <= ?)
                 ORDER BY next_attempt_at, created_at, event_id
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """;
        String lease = """
                UPDATE feedback_outbox
                   SET lease_until = ?, lease_token = ?
                 WHERE event_id = ?
                   AND published_at IS NULL
                   AND dead_lettered_at IS NULL
                """;

        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                List<FeedbackOutboxMessage> claimed = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(select)) {
                    statement.setTimestamp(1, Timestamp.from(now));
                    statement.setTimestamp(2, Timestamp.from(now));
                    statement.setInt(3, limit);
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            claimed.add(new FeedbackOutboxMessage(
                                    rows.getObject("event_id", UUID.class),
                                    rows.getObject("tenant_id", UUID.class),
                                    rows.getObject("aggregate_id", UUID.class),
                                    rows.getString("event_type"),
                                    rows.getInt("event_version"),
                                    rows.getString("payload"),
                                    rows.getTimestamp("occurred_at").toInstant(),
                                    rows.getInt("attempts"),
                                    UUID.randomUUID()));
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(lease)) {
                    for (FeedbackOutboxMessage message : claimed) {
                        statement.setTimestamp(1, Timestamp.from(leaseUntil));
                        statement.setObject(2, message.leaseToken());
                        statement.setObject(3, message.eventId());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
                return List.copyOf(claimed);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException failure) {
            throw new FeedbackPersistenceException("Unable to claim Feedback outbox events", failure);
        }
    }

    @Override
    public void markPublished(UUID eventId, UUID leaseToken, Instant publishedAt) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(leaseToken, "leaseToken");
        Objects.requireNonNull(publishedAt, "publishedAt");
        String sql = """
                UPDATE feedback_outbox
                   SET published_at = ?, lease_until = NULL, lease_token = NULL, last_error = NULL
                 WHERE event_id = ? AND lease_token = ? AND published_at IS NULL
                   AND dead_lettered_at IS NULL
                """;
        executeClaimedUpdate(sql, statement -> {
            statement.setTimestamp(1, Timestamp.from(publishedAt));
            statement.setObject(2, eventId);
            statement.setObject(3, leaseToken);
        }, "Outbox event lease is stale, missing, or already completed");
    }

    @Override
    public void recordFailure(UUID eventId, UUID leaseToken, String error, Instant nextAttemptAt, int maximumAttempts) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(leaseToken, "leaseToken");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        if (maximumAttempts < 1) throw new IllegalArgumentException("maximumAttempts must be positive");
        String sql = """
                UPDATE feedback_outbox
                   SET attempts = attempts + 1,
                       last_error = ?,
                       next_attempt_at = ?,
                       dead_lettered_at = CASE
                           WHEN attempts + 1 >= ? THEN CURRENT_TIMESTAMP
                           ELSE NULL
                       END,
                       lease_until = NULL,
                       lease_token = NULL
                 WHERE event_id = ? AND lease_token = ? AND published_at IS NULL
                   AND dead_lettered_at IS NULL
                """;
        String safeError = sanitize(error);
        executeClaimedUpdate(sql, statement -> {
            statement.setString(1, safeError);
            statement.setTimestamp(2, Timestamp.from(nextAttemptAt));
            statement.setInt(3, maximumAttempts);
            statement.setObject(4, eventId);
            statement.setObject(5, leaseToken);
        }, "Outbox event lease is stale, missing, or already completed");
    }

    private void executeClaimedUpdate(String sql, SqlBinder binder, String conflictMessage) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            if (statement.executeUpdate() != 1) throw new IllegalStateException(conflictMessage);
        } catch (SQLException failure) {
            throw new FeedbackPersistenceException("Unable to update Feedback outbox event", failure);
        }
    }

    private static String sanitize(String error) {
        if (error == null || error.isBlank()) return "Unspecified event delivery failure";
        String value = error.strip().replace('\n', ' ').replace('\r', ' ');
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    @FunctionalInterface
    private interface SqlBinder { void bind(PreparedStatement statement) throws SQLException; }
}
