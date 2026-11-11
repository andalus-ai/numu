package tech.kayys.numou.feedback.adapter.postgres;

import tech.kayys.numou.feedback.application.port.out.FeedbackAttachmentRepository;
import tech.kayys.numou.feedback.domain.FeedbackAttachment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter. Every read and write is scoped by tenant identity. */
public final class PostgresFeedbackAttachmentRepository implements FeedbackAttachmentRepository {
    private final DataSource dataSource;

    public PostgresFeedbackAttachmentRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public void save(FeedbackAttachment attachment) {
        java.util.Objects.requireNonNull(attachment, "attachment cannot be null");
        String sql = """
                INSERT INTO feedback_attachments
                    (id, tenant_id, feedback_id, document_id, document_version_id,
                     file_name, media_type, size_bytes, sha256, status, rejection_reason,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, id) DO UPDATE
                   SET status = EXCLUDED.status,
                       rejection_reason = EXCLUDED.rejection_reason,
                       updated_at = EXCLUDED.updated_at
                 WHERE feedback_attachments.document_id = EXCLUDED.document_id
                   AND feedback_attachments.document_version_id = EXCLUDED.document_version_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, attachment.id());
            statement.setObject(2, attachment.tenantId());
            statement.setObject(3, attachment.feedbackId().value());
            statement.setString(4, attachment.documentId());
            statement.setString(5, attachment.documentVersionId());
            statement.setString(6, attachment.fileName());
            statement.setString(7, attachment.mediaType());
            statement.setLong(8, attachment.sizeBytes());
            statement.setString(9, attachment.sha256());
            statement.setString(10, attachment.status().name());
            statement.setString(11, attachment.rejectionReason());
            statement.setTimestamp(12, Timestamp.from(attachment.createdAt()));
            statement.setTimestamp(13, Timestamp.from(attachment.updatedAt()));
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Attachment identity conflicts with different document metadata");
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to persist feedback attachment", e);
        }
    }

    @Override
    public Optional<FeedbackAttachment> findById(UUID tenantId, UUID attachmentId) {
        java.util.Objects.requireNonNull(tenantId, "tenantId cannot be null");
        java.util.Objects.requireNonNull(attachmentId, "attachmentId cannot be null");
        String sql = """
                SELECT id, tenant_id, feedback_id, document_id, document_version_id,
                       file_name, media_type, size_bytes, sha256, status, rejection_reason,
                       created_at, updated_at
                  FROM feedback_attachments
                 WHERE tenant_id = ? AND id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, attachmentId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(map(row)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to load feedback attachment", e);
        }
    }

    @Override
    public List<FeedbackAttachment> findByFeedback(UUID tenantId, UUID feedbackId) {
        java.util.Objects.requireNonNull(tenantId, "tenantId cannot be null");
        java.util.Objects.requireNonNull(feedbackId, "feedbackId cannot be null");
        String sql = """
                SELECT id, tenant_id, feedback_id, document_id, document_version_id,
                       file_name, media_type, size_bytes, sha256, status, rejection_reason,
                       created_at, updated_at
                  FROM feedback_attachments
                 WHERE tenant_id = ? AND feedback_id = ?
                 ORDER BY created_at, id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, feedbackId);
            try (ResultSet rows = statement.executeQuery()) {
                List<FeedbackAttachment> result = new ArrayList<>();
                while (rows.next()) result.add(map(rows));
                return List.copyOf(result);
            }
        } catch (SQLException e) {
            throw new FeedbackPersistenceException("Unable to list feedback attachments", e);
        }
    }

    private static FeedbackAttachment map(ResultSet row) throws SQLException {
        return FeedbackAttachment.restore(
                row.getObject("id", UUID.class),
                row.getObject("tenant_id", UUID.class),
                tech.kayys.numou.feedback.domain.FeedbackId.of(row.getObject("feedback_id", UUID.class)),
                row.getString("document_id"),
                row.getString("document_version_id"),
                row.getString("file_name"),
                row.getString("media_type"),
                row.getLong("size_bytes"),
                row.getString("sha256"),
                FeedbackAttachment.Status.valueOf(row.getString("status")),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(),
                row.getString("rejection_reason")
        );
    }
}
