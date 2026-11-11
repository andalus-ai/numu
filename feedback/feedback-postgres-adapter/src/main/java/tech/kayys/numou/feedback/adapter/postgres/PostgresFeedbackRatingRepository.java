package tech.kayys.numou.feedback.adapter.postgres;

import tech.kayys.numou.feedback.application.port.FeedbackRatingRepository;
import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.rating.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter for tenant-scoped rating responses and idempotent submissions. */
public final class PostgresFeedbackRatingRepository implements FeedbackRatingRepository {
    private final DataSource dataSource;

    public PostgresFeedbackRatingRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public FeedbackRatingId save(FeedbackRating rating, String idempotencyKey) {
        java.util.Objects.requireNonNull(rating, "rating cannot be null");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.trim().length() > 200) {
            throw new IllegalArgumentException("idempotencyKey must contain 1..200 characters");
        }
        String key = idempotencyKey.trim();
        try (Connection c = dataSource.getConnection()) {
            boolean previousAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                Optional<FeedbackRatingId> existing = findIdempotent(c, rating.tenantId(), key);
                if (existing.isPresent()) {
                    c.rollback();
                    return existing.get();
                }
                insertRating(c, rating);
                if (!insertIdempotency(c, rating, key)) {
                    c.rollback();
                    return findIdempotent(c, rating.tenantId(), key)
                            .orElseThrow(() -> new IllegalStateException("Idempotency conflict without original rating"));
                }
                c.commit();
                return rating.id();
            } catch (SQLException | RuntimeException failure) {
                c.rollback();
                throw failure;
            } finally {
                c.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to persist rating", e);
        }
    }

    @Override
    public Optional<FeedbackRating> findById(TenantId tenantId, FeedbackRatingId id) {
        java.util.Objects.requireNonNull(tenantId, "tenantId cannot be null");
        java.util.Objects.requireNonNull(id, "id cannot be null");
        String sql = "SELECT * FROM feedback_ratings WHERE tenant_id = ? AND id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId.value());
            s.setObject(2, id.value());
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(map(r)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to load rating", e);
        }
    }

    @Override
    public List<FeedbackRating> findByTarget(TenantId tenantId, RatingTarget target, RatingMetric metric) {
        java.util.Objects.requireNonNull(tenantId, "tenantId cannot be null");
        java.util.Objects.requireNonNull(target, "target cannot be null");
        java.util.Objects.requireNonNull(metric, "metric cannot be null");
        String sql = """
                SELECT * FROM feedback_ratings
                 WHERE tenant_id = ? AND target_type = ? AND target_id = ?
                   AND metric_code = ? AND branch_id IS NOT DISTINCT FROM ?
                 ORDER BY submitted_at ASC, id ASC
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId.value());
            s.setString(2, target.targetType());
            s.setString(3, target.targetId());
            s.setString(4, metric.name());
            s.setString(5, target.branchId());
            try (ResultSet r = s.executeQuery()) {
                List<FeedbackRating> results = new ArrayList<>();
                while (r.next()) results.add(map(r));
                return List.copyOf(results);
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to list ratings", e);
        }
    }

    private Optional<FeedbackRatingId> findIdempotent(Connection c, TenantId tenantId, String key)
            throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "SELECT rating_id FROM feedback_rating_idempotency WHERE tenant_id = ? AND idempotency_key = ?")) {
            s.setObject(1, tenantId.value());
            s.setString(2, key);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(FeedbackRatingId.of(r.getObject(1, UUID.class))) : Optional.empty();
            }
        }
    }

    private void insertRating(Connection c, FeedbackRating r) throws SQLException {
        String sql = """
                INSERT INTO feedback_ratings
                (id, tenant_id, target_type, target_id, branch_id, metric_code, scale_min, scale_max,
                 rating_value, respondent_type, respondent_id, survey_reference, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, r.id().value());
            s.setObject(2, r.tenantId().value());
            s.setString(3, r.target().targetType());
            s.setString(4, r.target().targetId());
            s.setString(5, r.target().branchId());
            s.setString(6, r.metric().name());
            s.setBigDecimal(7, r.scale().min());
            s.setBigDecimal(8, r.scale().max());
            s.setBigDecimal(9, r.value());
            if (r.respondent() == null) {
                s.setNull(10, Types.VARCHAR);
                s.setNull(11, Types.VARCHAR);
            } else {
                s.setString(10, r.respondent().actorType());
                s.setString(11, r.respondent().actorId());
            }
            s.setString(12, r.surveyReference());
            s.setTimestamp(13, Timestamp.from(r.submittedAt()));
            s.executeUpdate();
        }
    }

    private boolean insertIdempotency(Connection c, FeedbackRating r, String key) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO feedback_rating_idempotency (tenant_id, idempotency_key, rating_id)
                VALUES (?, ?, ?) ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """)) {
            s.setObject(1, r.tenantId().value());
            s.setString(2, key);
            s.setObject(3, r.id().value());
            return s.executeUpdate() == 1;
        }
    }

    private FeedbackRating map(ResultSet r) throws SQLException {
        String respondentType = r.getString("respondent_type");
        String respondentId = r.getString("respondent_id");
        FeedbackActor respondent = respondentType == null ? null : new FeedbackActor(respondentType, respondentId);
        return new FeedbackRating(FeedbackRatingId.of(r.getObject("id", UUID.class)),
                TenantId.of(r.getObject("tenant_id", UUID.class)),
                new RatingTarget(r.getString("target_type"), r.getString("target_id"), r.getString("branch_id")),
                RatingMetric.valueOf(r.getString("metric_code")),
                new RatingScale(r.getBigDecimal("scale_min"), r.getBigDecimal("scale_max")),
                r.getBigDecimal("rating_value"), respondent, r.getString("survey_reference"),
                r.getTimestamp("submitted_at").toInstant());
    }
}