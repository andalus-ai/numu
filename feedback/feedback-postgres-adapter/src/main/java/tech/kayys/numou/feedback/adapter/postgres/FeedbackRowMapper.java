package tech.kayys.numou.feedback.adapter.postgres;

import tech.kayys.numou.feedback.domain.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

final class FeedbackRowMapper {
    private FeedbackRowMapper() {}

    static FeedbackItem map(ResultSet row) throws SQLException {
        UUID id = row.getObject("id", UUID.class);
        UUID tenantId = row.getObject("tenant_id", UUID.class);
        UUID submitterId = row.getObject("submitter_id", UUID.class);
        return FeedbackItem.restore(
                FeedbackId.of(id), tenantId, submitterId,
                FeedbackType.valueOf(row.getString("feedback_type")),
                row.getString("title"), row.getString("description"),
                row.getString("source_application"), row.getString("source_module"),
                row.getString("source_reference"),
                FeedbackPriority.valueOf(row.getString("priority")),
                FeedbackStatus.valueOf(row.getString("status")),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(),
                row.getLong("version"));
    }
}