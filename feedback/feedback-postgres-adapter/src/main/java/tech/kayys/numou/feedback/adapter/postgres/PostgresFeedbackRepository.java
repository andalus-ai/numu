package tech.kayys.numou.feedback.adapter.postgres;

import tech.kayys.numou.feedback.application.port.FeedbackRepository;
import tech.kayys.numou.feedback.domain.FeedbackEvent;
import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.feedback.domain.FeedbackItem;
import tech.kayys.numou.feedback.domain.FeedbackStatusChanged;
import tech.kayys.numou.feedback.domain.FeedbackSubmitted;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC adapter for PostgreSQL. The feedback row, idempotency key and domain outbox
 * records are committed in one local transaction.
 */
public final class PostgresFeedbackRepository implements FeedbackRepository {
    private final DataSource dataSource;

    public PostgresFeedbackRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public FeedbackId save(FeedbackItem feedback, String idempotencyKey) {
        java.util.Objects.requireNonNull(feedback, "feedback cannot be null");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
            throw new IllegalArgumentException("idempotencyKey must contain 1..200 characters");
        }

        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Optional<FeedbackId> existing = findIdempotent(connection, feedback.tenantId(), idempotencyKey);
                if (existing.isPresent()) {
                    connection.rollback();
                    return existing.get();
                }

                insertFeedback(connection, feedback);
                insertIdempotency(connection, feedback, idempotencyKey);
                insertOutbox(connection, feedback);

                connection.commit();
                feedback.clearPendingEvents();
                return feedback.id();
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to persist feedback submission", e);
        }
    }

    @Override
    public Optional<FeedbackItem> findById(UUID tenantId, FeedbackId feedbackId) {
        java.util.Objects.requireNonNull(tenantId, "tenantId cannot be null");
        java.util.Objects.requireNonNull(feedbackId, "feedbackId cannot be null");
        String sql = """
                SELECT id, tenant_id, submitter_id, feedback_type, title, description,
                       source_application, source_module, source_reference, priority,
                       status, version, created_at, updated_at
                  FROM feedback_items
                 WHERE tenant_id = ? AND id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, feedbackId.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                return Optional.of(FeedbackRowMapper.map(row));
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to load feedback item", e);
        }
    }

    private Optional<FeedbackId> findIdempotent(Connection connection, UUID tenantId, String key)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT feedback_id FROM feedback_idempotency WHERE tenant_id = ? AND idempotency_key = ?")) {
            statement.setObject(1, tenantId);
            statement.setString(2, key);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(FeedbackId.of(row.getObject(1, UUID.class))) : Optional.empty();
            }
        }
    }

    private void insertFeedback(Connection c, FeedbackItem f) throws SQLException {
        String sql = """
                INSERT INTO feedback_items
                (id, tenant_id, submitter_id, feedback_type, title, description,
                 source_application, source_module, source_reference, priority, status,
                 version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, f.id().value());
            s.setObject(2, f.tenantId());
            if (f.submitterId() == null) s.setNull(3, Types.OTHER); else s.setObject(3, f.submitterId());
            s.setString(4, f.type().name());
            s.setString(5, f.title());
            s.setString(6, f.description());
            s.setString(7, f.sourceApplication());
            s.setString(8, f.sourceModule());
            s.setString(9, f.sourceReference());
            s.setString(10, f.priority().name());
            s.setString(11, f.status().name());
            s.setLong(12, f.version());
            s.setTimestamp(13, Timestamp.from(f.createdAt()));
            s.setTimestamp(14, Timestamp.from(f.updatedAt()));
            s.executeUpdate();
        }
    }

    private void insertIdempotency(Connection c, FeedbackItem f, String key) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "INSERT INTO feedback_idempotency (tenant_id, idempotency_key, feedback_id) VALUES (?, ?, ?)")) {
            s.setObject(1, f.tenantId());
            s.setString(2, key);
            s.setObject(3, f.id().value());
            s.executeUpdate();
        }
    }

    private void insertOutbox(Connection c, FeedbackItem f) throws SQLException {
        String sql = """
                INSERT INTO feedback_outbox
                (event_id, tenant_id, aggregate_id, event_type, event_version, payload, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                """;
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (FeedbackEvent event : f.pendingEvents()) {
                s.setObject(1, event.eventId());
                s.setObject(2, event.tenantId());
                s.setObject(3, event.feedbackId().value());
                s.setString(4, event.getClass().getSimpleName());
                s.setInt(5, 1);
                s.setString(6, eventPayload(event));
                s.setTimestamp(7, Timestamp.from(event.occurredAt()));
                s.addBatch();
            }
            s.executeBatch();
        }
    }

    private String eventPayload(FeedbackEvent event) {
        if (event instanceof FeedbackSubmitted submitted) {
            return "{\"feedbackId\":\"" + submitted.feedbackId()
                    + "\",\"feedbackType\":\"" + submitted.type().name() + "\"}";
        }
        if (event instanceof FeedbackStatusChanged changed) {
            return "{\"feedbackId\":\"" + changed.feedbackId()
                    + "\",\"previousStatus\":\"" + changed.previousStatus().name()
                    + "\",\"newStatus\":\"" + changed.newStatus().name() + "\"}";
        }
        throw new IllegalArgumentException("Unsupported feedback event: " + event.getClass().getName());
    }
}