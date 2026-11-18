package tech.kayys.numou.feedback.adapter.postgres;

import tech.kayys.numou.feedback.application.port.FeedbackCommentRepository;
import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.feedback.domain.comment.FeedbackComment;
import tech.kayys.numou.feedback.domain.comment.FeedbackCommentId;
import tech.kayys.numou.feedback.domain.comment.FeedbackCommentVisibility;
import tech.kayys.numou.feedback.domain.event.FeedbackCommentAdded;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped PostgreSQL adapter; comment and event commit in one transaction. */
public final class PostgresFeedbackCommentRepository implements FeedbackCommentRepository {
    private final DataSource dataSource;

    public PostgresFeedbackCommentRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public FeedbackCommentId save(FeedbackComment comment, String idempotencyKey) {
        java.util.Objects.requireNonNull(comment, "comment cannot be null");
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.trim().length() > 200) {
            throw new IllegalArgumentException("idempotencyKey must contain 1..200 characters");
        }
        String key = idempotencyKey.trim();
        try (Connection c = dataSource.getConnection()) {
            boolean previousAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                Optional<FeedbackCommentId> existing = findIdempotent(c, comment.tenantId(), key);
                if (existing.isPresent()) {
                    c.rollback();
                    return existing.get();
                }
                insertComment(c, comment);
                if (!insertIdempotency(c, comment, key)) {
                    c.rollback();
                    return findIdempotent(c, comment.tenantId(), key)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Idempotency conflict without original feedback comment"));
                }
                insertOutbox(c, comment);
                c.commit();
                return comment.id();
            } catch (SQLException | RuntimeException failure) {
                c.rollback();
                throw failure;
            } finally {
                c.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to persist feedback comment", e);
        }
    }

    @Override
    public Optional<FeedbackComment> findById(TenantId tenantId, FeedbackCommentId commentId) {
        java.util.Objects.requireNonNull(tenantId, "tenantId cannot be null");
        java.util.Objects.requireNonNull(commentId, "commentId cannot be null");
        String sql = "SELECT * FROM feedback_comments WHERE tenant_id = ? AND id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId.value());
            s.setObject(2, commentId.value());
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(map(r)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to load feedback comment", e);
        }
    }

    @Override
    public List<FeedbackComment> findByFeedback(TenantId tenantId, FeedbackId feedbackId,
                                                 FeedbackCommentVisibility visibility, int limit, int offset) {
        java.util.Objects.requireNonNull(tenantId, "tenantId cannot be null");
        java.util.Objects.requireNonNull(feedbackId, "feedbackId cannot be null");
        java.util.Objects.requireNonNull(visibility, "visibility cannot be null");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        if (offset < 0) throw new IllegalArgumentException("offset cannot be negative");
        String sql = """
                SELECT * FROM feedback_comments
                 WHERE tenant_id = ? AND feedback_id = ? AND visibility = ?
                 ORDER BY created_at ASC, id ASC
                 LIMIT ? OFFSET ?
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId.value());
            s.setObject(2, feedbackId.value());
            s.setString(3, visibility.name());
            s.setInt(4, limit);
            s.setInt(5, offset);
            try (ResultSet r = s.executeQuery()) {
                List<FeedbackComment> results = new ArrayList<>();
                while (r.next()) results.add(map(r));
                return List.copyOf(results);
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to list feedback comments", e);
        }
    }

    private Optional<FeedbackCommentId> findIdempotent(Connection c, TenantId tenantId, String key)
            throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                SELECT comment_id FROM feedback_comment_idempotency
                 WHERE tenant_id = ? AND idempotency_key = ?
                """)) {
            s.setObject(1, tenantId.value());
            s.setString(2, key);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(FeedbackCommentId.of(r.getObject(1, UUID.class)))
                        : Optional.empty();
            }
        }
    }

    private void insertComment(Connection c, FeedbackComment comment) throws SQLException {
        String sql = """
                INSERT INTO feedback_comments
                (id, tenant_id, feedback_id, parent_comment_id, author_type, author_id,
                 visibility, body, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, comment.id().value());
            s.setObject(2, comment.tenantId().value());
            s.setObject(3, comment.feedbackId().value());
            if (comment.parentCommentId() == null) s.setNull(4, Types.OTHER);
            else s.setObject(4, comment.parentCommentId().value());
            s.setString(5, comment.author().actorType());
            s.setString(6, comment.author().actorId());
            s.setString(7, comment.visibility().name());
            s.setString(8, comment.body());
            s.setTimestamp(9, Timestamp.from(comment.createdAt()));
            s.executeUpdate();
        }
    }

    private boolean insertIdempotency(Connection c, FeedbackComment comment, String key) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO feedback_comment_idempotency (tenant_id, idempotency_key, comment_id)
                VALUES (?, ?, ?) ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """)) {
            s.setObject(1, comment.tenantId().value());
            s.setString(2, key);
            s.setObject(3, comment.id().value());
            return s.executeUpdate() == 1;
        }
    }

    private void insertOutbox(Connection c, FeedbackComment comment) throws SQLException {
        FeedbackCommentAdded event = new FeedbackCommentAdded(UUID.randomUUID(),
                comment.createdAt(), comment.feedbackId(), comment.id(),
                comment.parentCommentId(), comment.visibility());
        String payload = "{"
                + "\"feedbackId\":" + quote(comment.feedbackId().value().toString()) + ","
                + "\"commentId\":" + quote(comment.id().value().toString()) + ","
                + "\"parentCommentId\":" + nullableQuote(comment.parentCommentId() == null
                    ? null : comment.parentCommentId().value().toString()) + ","
                + "\"visibility\":" + quote(comment.visibility().name())
                + "}";
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO feedback_engagement_outbox
                (event_id, tenant_id, aggregate_type, aggregate_id, event_type,
                 event_version, payload, occurred_at)
                VALUES (?, ?, 'FEEDBACK', ?, ?, 1, ?::jsonb, ?)
                """)) {
            s.setObject(1, event.eventId());
            s.setObject(2, comment.tenantId().value());
            s.setObject(3, comment.feedbackId().value());
            s.setString(4, event.eventType());
            s.setString(5, payload);
            s.setTimestamp(6, Timestamp.from(event.occurredAt()));
            s.executeUpdate();
        }
    }

    private static FeedbackComment map(ResultSet r) throws SQLException {
        UUID parent = r.getObject("parent_comment_id", UUID.class);
        Instant createdAt = r.getTimestamp("created_at").toInstant();
        return FeedbackComment.add(
                FeedbackCommentId.of(r.getObject("id", UUID.class)),
                TenantId.of(r.getObject("tenant_id", UUID.class)),
                FeedbackId.of(r.getObject("feedback_id", UUID.class)),
                parent == null ? null : FeedbackCommentId.of(parent),
                new FeedbackActor(r.getString("author_type"), r.getString("author_id")),
                FeedbackCommentVisibility.valueOf(r.getString("visibility")),
                r.getString("body"), createdAt);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String nullableQuote(String value) {
        return value == null ? "null" : quote(value);
    }
}