package tech.kayys.numou.feedback.adapter.postgres;

import tech.kayys.numou.feedback.application.port.FeedbackRepository;
import tech.kayys.numou.feedback.domain.*;
import tech.kayys.numou.feedback.domain.event.FeedbackStatusChanged;
import tech.kayys.numou.feedback.domain.event.FeedbackSubmitted;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter. Aggregate, idempotency mapping and outbox rows commit atomically. */
public final class PostgresFeedbackRepository implements FeedbackRepository {
    private final DataSource dataSource;

    public PostgresFeedbackRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public FeedbackId save(Feedback feedback, String idempotencyKey) {
        java.util.Objects.requireNonNull(feedback, "feedback cannot be null");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.trim().length() > 200) {
            throw new IllegalArgumentException("idempotencyKey must contain 1..200 characters");
        }
        String key = idempotencyKey.trim();

        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Optional<FeedbackId> existing = findIdempotent(connection, feedback.tenantId().value(), key);
                if (existing.isPresent()) {
                    connection.rollback();
                    return existing.get();
                }

                insertFeedback(connection, feedback);
                if (!insertIdempotency(connection, feedback, key)) {
                    connection.rollback();
                    return findIdempotent(connection, feedback.tenantId().value(), key)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Idempotency key conflict was reported but no original submission exists"));
                }
                insertOutbox(connection, feedback);
                connection.commit();
                feedback.clearEvents();
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
    public Optional<Feedback> findById(TenantId tenantId, FeedbackId feedbackId) {
        java.util.Objects.requireNonNull(tenantId, "tenantId cannot be null");
        java.util.Objects.requireNonNull(feedbackId, "feedbackId cannot be null");
        String sql = """
                SELECT id, tenant_id, author_type, author_id, kind_code, priority, title, description,
                       context_application, context_module, context_screen, context_record_type,
                       context_record_reference, context_application_version, status, version,
                       created_at, updated_at
                  FROM feedback_items
                 WHERE tenant_id = ? AND id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId.value());
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

    private void insertFeedback(Connection c, Feedback f) throws SQLException {
        String sql = """
                INSERT INTO feedback_items
                (id, tenant_id, author_type, author_id, kind_code, priority, title, description,
                 context_application, context_module, context_screen, context_record_type,
                 context_record_reference, context_application_version, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        FeedbackContext context = f.context();
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, f.id().value());
            s.setObject(2, f.tenantId().value());
            s.setString(3, f.author().actorType());
            s.setString(4, f.author().actorId());
            s.setString(5, f.kind().code());
            s.setString(6, f.priority().name());
            s.setString(7, f.title());
            s.setString(8, f.description());
            s.setString(9, context.application());
            s.setString(10, context.module());
            s.setString(11, context.screen());
            s.setString(12, context.recordType());
            s.setString(13, context.recordReference());
            s.setString(14, context.applicationVersion());
            s.setString(15, f.status().name());
            s.setInt(16, f.version());
            s.setTimestamp(17, Timestamp.from(f.createdAt()));
            s.setTimestamp(18, Timestamp.from(f.updatedAt()));
            s.executeUpdate();
        }
    }

    private boolean insertIdempotency(Connection c, Feedback f, String key) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO feedback_idempotency (tenant_id, idempotency_key, feedback_id)
                VALUES (?, ?, ?) ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """)) {
            s.setObject(1, f.tenantId().value());
            s.setString(2, key);
            s.setObject(3, f.id().value());
            return s.executeUpdate() == 1;
        }
    }

    private void insertOutbox(Connection c, Feedback f) throws SQLException {
        String sql = """
                INSERT INTO feedback_outbox
                (event_id, tenant_id, aggregate_id, event_type, event_version, payload, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                """;
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (DomainEvent event : f.getDomainEvents()) {
                s.setObject(1, event.eventId());
                s.setObject(2, f.tenantId().value());
                s.setObject(3, f.id().value());
                s.setString(4, event.eventType());
                s.setInt(5, 1);
                s.setString(6, eventPayload(event));
                s.setTimestamp(7, Timestamp.from(event.occurredAt()));
                s.addBatch();
            }
            s.executeBatch();
        }
    }

    private String eventPayload(DomainEvent event) {
        if (event instanceof FeedbackSubmitted submitted) {
            return "{\"feedbackId\":\"" + submitted.feedbackId()
                    + "\",\"kindCode\":\"" + submitted.kindCode() + "\"}";
        }
        if (event instanceof FeedbackStatusChanged changed) {
            return "{\"feedbackId\":\"" + changed.feedbackId()
                    + "\",\"previousStatus\":\"" + changed.previousStatus().name()
                    + "\",\"newStatus\":\"" + changed.newStatus().name() + "\"}";
        }
        throw new IllegalArgumentException("Unsupported feedback event: " + event.getClass().getName());
    }
}