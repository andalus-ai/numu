package tech.kayys.numou.feedback.adapter.postgres;

import tech.kayys.numou.feedback.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

final class FeedbackRowMapper {
    private FeedbackRowMapper() {}

    static Feedback map(ResultSet row) throws SQLException {
        UUID id = row.getObject("id", UUID.class);
        UUID tenantId = row.getObject("tenant_id", UUID.class);
        FeedbackActor author = new FeedbackActor(
                row.getString("author_type"), row.getString("author_id"));
        FeedbackContext context = new FeedbackContext(
                row.getString("context_application"),
                row.getString("context_module"),
                row.getString("context_screen"),
                row.getString("context_record_type"),
                row.getString("context_record_reference"),
                row.getString("context_application_version"));
        Instant createdAt = row.getTimestamp("created_at").toInstant();
        Instant updatedAt = row.getTimestamp("updated_at").toInstant();
        return Feedback.rehydrate(
                FeedbackId.of(id), TenantId.of(tenantId), author,
                FeedbackKind.of(row.getString("kind_code")),
                FeedbackPriority.valueOf(row.getString("priority")),
                row.getString("title"), row.getString("description"), context,
                FeedbackStatus.valueOf(row.getString("status")),
                createdAt, updatedAt, row.getInt("version"));
    }
}
