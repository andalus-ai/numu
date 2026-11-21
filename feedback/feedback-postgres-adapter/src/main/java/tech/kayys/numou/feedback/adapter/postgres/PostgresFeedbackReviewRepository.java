package tech.kayys.numou.feedback.adapter.postgres;

import tech.kayys.numou.feedback.application.port.FeedbackReviewRepository;
import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.rating.FeedbackRatingId;
import tech.kayys.numou.feedback.domain.rating.RatingTarget;
import tech.kayys.numou.feedback.domain.review.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter for moderated reviews. All reads are tenant-scoped. */
public final class PostgresFeedbackReviewRepository implements FeedbackReviewRepository {
    private final DataSource dataSource;

    public PostgresFeedbackReviewRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public void save(FeedbackReview review) {
        String sql = """
                INSERT INTO feedback_reviews
                (id, tenant_id, target_type, target_id, branch_id, author_type, author_id, rating_id,
                 title, body, status, verification_status, created_at, moderated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = dataSource.getConnection()) {
            boolean previousAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement s = c.prepareStatement(sql)) {
                s.setObject(1, review.id().value());
                s.setObject(2, review.tenantId().value());
                s.setString(3, review.target().targetType());
                s.setString(4, review.target().targetId());
                s.setString(5, review.target().branchId());
                s.setString(6, review.author().actorType());
                s.setString(7, review.author().actorId());
                s.setObject(8, review.ratingId().value());
                s.setString(9, review.title());
                s.setString(10, review.body());
                s.setString(11, review.status().name());
                s.setString(12, review.verificationStatus().name());
                s.setTimestamp(13, Timestamp.from(review.createdAt()));
                if (review.moderatedAt() == null) s.setNull(14, Types.TIMESTAMP_WITH_TIMEZONE);
                else s.setTimestamp(14, Timestamp.from(review.moderatedAt()));
                s.executeUpdate();
                insertReviewEvent(c, review, "feedback.review-submitted.v1", null, null);
                c.commit();
            } catch (SQLException | RuntimeException failure) {
                c.rollback();
                throw failure;
            } finally {
                c.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to persist review", e);
        }
    }

    @Override
    public void update(FeedbackReview review) {
        try (Connection c = dataSource.getConnection()) {
            boolean previousAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                String previousStatus;
                String previousVerification;
                try (PreparedStatement read = c.prepareStatement(
                        "SELECT status, verification_status FROM feedback_reviews WHERE tenant_id = ? AND id = ? FOR UPDATE")) {
                    read.setObject(1, review.tenantId().value());
                    read.setObject(2, review.id().value());
                    try (ResultSet row = read.executeQuery()) {
                        if (!row.next()) throw new IllegalStateException("Review not found for tenant");
                        previousStatus = row.getString("status");
                        previousVerification = row.getString("verification_status");
                    }
                }
                try (PreparedStatement update = c.prepareStatement("""
                        UPDATE feedback_reviews
                           SET status = ?, verification_status = ?, moderated_at = ?
                         WHERE tenant_id = ? AND id = ?
                        """)) {
                    update.setString(1, review.status().name());
                    update.setString(2, review.verificationStatus().name());
                    if (review.moderatedAt() == null) update.setNull(3, Types.TIMESTAMP_WITH_TIMEZONE);
                    else update.setTimestamp(3, Timestamp.from(review.moderatedAt()));
                    update.setObject(4, review.tenantId().value());
                    update.setObject(5, review.id().value());
                    if (update.executeUpdate() != 1) throw new IllegalStateException("Review not found for tenant");
                }
                if (!previousStatus.equals(review.status().name())
                        || !previousVerification.equals(review.verificationStatus().name())) {
                    insertReviewEvent(c, review, "feedback.review-moderated.v1",
                            previousStatus, previousVerification);
                }
                c.commit();
            } catch (SQLException | RuntimeException failure) {
                c.rollback();
                throw failure;
            } finally {
                c.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to update review moderation state", e);
        }
    }

    @Override
    public Optional<FeedbackReview> findById(TenantId tenantId, FeedbackReviewId id) {
        String sql = "SELECT * FROM feedback_reviews WHERE tenant_id = ? AND id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId.value());
            s.setObject(2, id.value());
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return Optional.empty();
                return Optional.of(map(r));
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to load review", e);
        }
    }

    @Override
    public java.util.List<FeedbackReview> findByTarget(TenantId tenantId, RatingTarget target,
                                                        ReviewStatus status, int limit, int offset) {
        java.util.Objects.requireNonNull(tenantId, "tenantId cannot be null");
        java.util.Objects.requireNonNull(target, "target cannot be null");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        if (offset < 0) throw new IllegalArgumentException("offset cannot be negative");
        String sql = """
                SELECT * FROM feedback_reviews
                 WHERE tenant_id = ? AND target_type = ? AND target_id = ?
                   AND branch_id IS NOT DISTINCT FROM ?
                   AND (? IS NULL OR status = ?)
                 ORDER BY created_at DESC, id DESC
                 LIMIT ? OFFSET ?
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId.value());
            s.setString(2, target.targetType());
            s.setString(3, target.targetId());
            s.setString(4, target.branchId());
            if (status == null) {
                s.setNull(5, Types.VARCHAR);
                s.setNull(6, Types.VARCHAR);
            } else {
                s.setString(5, status.name());
                s.setString(6, status.name());
            }
            s.setInt(7, limit);
            s.setInt(8, offset);
            try (ResultSet rows = s.executeQuery()) {
                java.util.List<FeedbackReview> results = new java.util.ArrayList<>();
                while (rows.next()) results.add(map(rows));
                return java.util.List.copyOf(results);
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to list reviews", e);
        }
    }

    private void insertReviewEvent(Connection c, FeedbackReview review, String eventType,
                                   String previousStatus, String previousVerification) throws SQLException {
        String payload = "{"
                + "\"reviewId\":" + quote(review.id().value().toString()) + ","
                + "\"ratingId\":" + quote(review.ratingId().value().toString()) + ","
                + "\"targetType\":" + quote(review.target().targetType()) + ","
                + "\"targetId\":" + quote(review.target().targetId()) + ","
                + "\"status\":" + quote(review.status().name()) + ","
                + "\"verificationStatus\":" + quote(review.verificationStatus().name()) + ","
                + "\"previousStatus\":" + nullableQuote(previousStatus) + ","
                + "\"previousVerificationStatus\":" + nullableQuote(previousVerification)
                + "}";
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO feedback_engagement_outbox
                (event_id, tenant_id, aggregate_type, aggregate_id, event_type, event_version, payload, occurred_at)
                VALUES (?, ?, 'REVIEW', ?, ?, 1, ?::jsonb, ?)
                """)) {
            s.setObject(1, UUID.randomUUID());
            s.setObject(2, review.tenantId().value());
            s.setObject(3, review.id().value());
            s.setString(4, eventType);
            s.setString(5, payload);
            s.setTimestamp(6, Timestamp.from(review.moderatedAt() == null
                    ? review.createdAt() : review.moderatedAt()));
            s.executeUpdate();
        }
    }

    private String nullableQuote(String value) { return value == null ? "null" : quote(value); }

    private String quote(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '\"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) escaped.append(String.format("\\u%04x", (int) ch));
                    else escaped.append(ch);
                }
            }
        }
        return escaped.append('\"').toString();
    }

    private FeedbackReview map(ResultSet r) throws SQLException {
        Timestamp moderated = r.getTimestamp("moderated_at");
        return FeedbackReview.rehydrate(
                FeedbackReviewId.of(r.getObject("id", UUID.class)),
                TenantId.of(r.getObject("tenant_id", UUID.class)),
                new RatingTarget(r.getString("target_type"), r.getString("target_id"), r.getString("branch_id")),
                new FeedbackActor(r.getString("author_type"), r.getString("author_id")),
                FeedbackRatingId.of(r.getObject("rating_id", UUID.class)),
                r.getString("title"), r.getString("body"),
                ReviewStatus.valueOf(r.getString("status")),
                VerificationStatus.valueOf(r.getString("verification_status")),
                r.getTimestamp("created_at").toInstant(),
                moderated == null ? null : moderated.toInstant());
    }
}