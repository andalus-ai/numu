package tech.kayys.numou.feedback.adapter.postgres;

import tech.kayys.numou.feedback.application.port.FeedbackSurveyCatalogRepository;
import tech.kayys.numou.feedback.application.port.SurveyDistributionRepository;
import tech.kayys.numou.feedback.domain.survey.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL persistence for versioned feedback templates and invitation grants. */
public final class PostgresFeedbackSurveyRepository
        implements FeedbackSurveyCatalogRepository, SurveyDistributionRepository {
    private final DataSource dataSource;

    public PostgresFeedbackSurveyRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public void save(FeedbackSurveyTemplate template) {
        java.util.Objects.requireNonNull(template, "template cannot be null");
        String sql = """
                INSERT INTO feedback_survey_templates
                (tenant_id, template_code, template_version, title, description, survey_definition,
                 definition_sha256, status, created_at, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, template_code, template_version) DO NOTHING
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, template.tenantId().value());
            s.setString(2, template.code());
            s.setInt(3, template.version());
            s.setString(4, template.title());
            s.setString(5, template.description());
            s.setString(6, template.surveyDefinition());
            s.setString(7, template.checksum());
            s.setString(8, template.status().name());
            s.setTimestamp(9, Timestamp.from(template.createdAt()));
            if (template.publishedAt() == null) s.setNull(10, Types.TIMESTAMP_WITH_TIMEZONE);
            else s.setTimestamp(10, Timestamp.from(template.publishedAt()));
            if (s.executeUpdate() == 0) {
                Optional<FeedbackSurveyTemplate> existing = find(template.tenantId(), template.code(), template.version());
                if (existing.isEmpty() || !existing.get().equals(template))
                    throw new IllegalStateException("A different immutable template version already exists");
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to save survey template", e);
        }
    }

    @Override
    public Optional<FeedbackSurveyTemplate> find(TenantId tenantId, String code, int version) {
        String sql = "SELECT * FROM feedback_survey_templates WHERE tenant_id = ? AND template_code = ? AND template_version = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId.value());
            s.setString(2, code);
            s.setInt(3, version);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(mapTemplate(r)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to load survey template", e);
        }
    }

    @Override
    public Optional<FeedbackSurveyTemplate> findLatestPublished(TenantId tenantId, String code) {
        String sql = """
                SELECT * FROM feedback_survey_templates
                 WHERE tenant_id = ? AND template_code = ? AND status = 'PUBLISHED'
                 ORDER BY template_version DESC LIMIT 1
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId.value());
            s.setString(2, code);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(mapTemplate(r)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to load published survey template", e);
        }
    }

    @Override
    public List<FeedbackSurveyTemplate> list(TenantId tenantId, String code, int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be in 1..500");
        String sql = """
                SELECT * FROM feedback_survey_templates
                 WHERE tenant_id = ? AND (?::varchar IS NULL OR template_code = ?)
                 ORDER BY template_code, template_version DESC LIMIT ?
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId.value());
            s.setString(2, code);
            s.setString(3, code);
            s.setInt(4, limit);
            try (ResultSet r = s.executeQuery()) {
                List<FeedbackSurveyTemplate> result = new ArrayList<>();
                while (r.next()) result.add(mapTemplate(r));
                return List.copyOf(result);
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to list survey templates", e);
        }
    }

    @Override
    public void save(SurveyDistribution distribution) {
        java.util.Objects.requireNonNull(distribution, "distribution cannot be null");
        String insert = """
                INSERT INTO feedback_survey_distributions
                (id, tenant_id, survey_id, channel, token_hash, expires_at, max_responses, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement s = c.prepareStatement(insert)) {
                    s.setObject(1, distribution.id());
                    s.setObject(2, distribution.tenantId().value());
                    s.setString(3, distribution.surveyId());
                    s.setString(4, distribution.channel().name());
                    s.setString(5, distribution.tokenHash());
                    s.setTimestamp(6, Timestamp.from(distribution.expiresAt()));
                    s.setInt(7, distribution.maxResponses());
                    s.setString(8, distribution.status().name());
                    s.setTimestamp(9, Timestamp.from(distribution.createdAt()));
                    s.executeUpdate();
                }
                String payload = "{\"distributionId\":\"" + distribution.id()
                        + "\",\"surveyId\":\"" + jsonEscape(distribution.surveyId())
                        + "\",\"channel\":\"" + distribution.channel().name() + "\"}";
                try (PreparedStatement s = c.prepareStatement("""
                        INSERT INTO feedback_engagement_outbox
                        (event_id, tenant_id, aggregate_type, aggregate_id, event_type, event_version, payload, occurred_at)
                        VALUES (?, ?, 'SURVEY_DISTRIBUTION', ?, 'feedback.survey-distribution-created.v1', 1, ?::jsonb, ?)
                        """)) {
                    s.setObject(1, UUID.randomUUID());
                    s.setObject(2, distribution.tenantId().value());
                    s.setObject(3, distribution.id());
                    s.setString(4, payload);
                    s.setTimestamp(5, Timestamp.from(distribution.createdAt()));
                    s.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException failure) {
                c.rollback();
                throw failure;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to save survey distribution", e);
        }
    }

    @Override
    public Optional<SurveyDistribution> findById(TenantId tenantId, UUID id) {
        String sql = "SELECT * FROM feedback_survey_distributions WHERE tenant_id = ? AND id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId.value());
            s.setObject(2, id);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(mapDistribution(r)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to load survey distribution", e);
        }
    }

    @Override
    public Optional<SurveyDistribution> findActiveByTokenHash(String tokenHash) {
        String sql = "SELECT * FROM feedback_survey_distributions WHERE token_hash = ? AND status = 'ACTIVE' AND expires_at > CURRENT_TIMESTAMP";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, tokenHash);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(mapDistribution(r)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to resolve survey invitation", e);
        }
    }

    private FeedbackSurveyTemplate mapTemplate(ResultSet r) throws SQLException {
        Timestamp published = r.getTimestamp("published_at");
        return new FeedbackSurveyTemplate(TenantId.of(r.getObject("tenant_id", UUID.class)),
                r.getString("template_code"), r.getInt("template_version"), r.getString("title"),
                r.getString("description"), r.getString("survey_definition"), r.getString("definition_sha256"),
                TemplateStatus.valueOf(r.getString("status")), r.getTimestamp("created_at").toInstant(),
                published == null ? null : published.toInstant());
    }

    private SurveyDistribution mapDistribution(ResultSet r) throws SQLException {
        return new SurveyDistribution(r.getObject("id", UUID.class),
                TenantId.of(r.getObject("tenant_id", UUID.class)), r.getString("survey_id"),
                DistributionChannel.valueOf(r.getString("channel")), r.getString("token_hash"),
                r.getTimestamp("expires_at").toInstant(), r.getInt("max_responses"),
                DistributionStatus.valueOf(r.getString("status")), r.getTimestamp("created_at").toInstant());
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
