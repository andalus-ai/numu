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
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
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
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to persist review", e);
        }
    }

    @Override
    public void update(FeedbackReview review) {
        String sql = """
                UPDATE feedback_reviews
                   SET status = ?, verification_status = ?, moderated_at = ?
                 WHERE tenant_id = ? AND id = ?
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, review.status().name());
            s.setString(2, review.verificationStatus().name());
            if (review.moderatedAt() == null) s.setNull(3, Types.TIMESTAMP_WITH_TIMEZONE);
            else s.setTimestamp(3, Timestamp.from(review.moderatedAt()));
            s.setObject(4, review.tenantId().value());
            s.setObject(5, review.id().value());
            if (s.executeUpdate() != 1) throw new IllegalStateException("Review not found for tenant");
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