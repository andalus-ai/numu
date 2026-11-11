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
import java.util.Objects;
import java.util.UUID;

/**
 * PostgreSQL adapter. Attachment state and its integration outbox event are committed
 * in one local transaction. Every read and write is scoped by tenant identity.
 */
public final class PostgresFeedbackAttachmentRepository implements FeedbackAttachmentRepository {
    private final DataSource dataSource;

    public PostgresFeedbackAttachmentRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public void save(FeedbackAttachment attachment) {
        java.util.Objects.requireNonNull(attachment, "attachment cannot be null");
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Existing current = findCurrent(connection, attachment.tenantId(), attachment.id());
                if (current == null) {
                    insert(connection, attachment);
                    appendOutbox(connection, attachment, "feedback.attachment-added");
                } else {
                    verifyImmutableMetadata(current, attachment);
                    if (attachment.updatedAt().isBefore(current.updatedAt())) {
                        throw new IllegalStateException("Stale attachment state cannot overwrite a newer version");
                    }
                    if (attachment.updatedAt().equals(current.updatedAt())
                            && (current.status() != attachment.status()
                                || !Objects.equals(current.rejectionReason(), attachment.rejectionReason()))) {
                        throw new IllegalStateException("Conflicting attachment states share the same update timestamp");
                    }
                    if (current.status() != attachment.status()
                            || !current.updatedAt().equals(attachment.updatedAt())
                            || !Objects.equals(current.rejectionReason(), attachment.rejectionReason())) {
                        updateState(connection, attachment);
                        appendOutbox(connection, attachment, "feedback.attachment-status-changed");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
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

    private Existing findCurrent(Connection connection, UUID tenantId, UUID attachmentId) throws SQLException {
        String sql = """
                SELECT feedback_id, document_id, document_version_id, file_name, media_type,
                       size_bytes, sha256, status, rejection_reason, updated_at
                  FROM feedback_attachments
                 WHERE tenant_id = ? AND id = ?
                 FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, attachmentId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                return new Existing(
                        row.getObject("feedback_id", UUID.class),
                        row.getString("document_id"),
                        row.getString("document_version_id"),
                        row.getString("file_name"),
                        row.getString("media_type"),
                        row.getLong("size_bytes"),
                        row.getString("sha256"),
                        FeedbackAttachment.Status.valueOf(row.getString("status")),
                        row.getString("rejection_reason"),
                        row.getTimestamp("updated_at").toInstant());
            }
        }
    }

    private void insert(Connection connection, FeedbackAttachment attachment) throws SQLException {
        String sql = """
                INSERT INTO feedback_attachments
                    (id, tenant_id, feedback_id, document_id, document_version_id,
                     file_name, media_type, size_bytes, sha256, status, rejection_reason,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
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
            statement.executeUpdate();
        }
    }

    private void updateState(Connection connection, FeedbackAttachment attachment) throws SQLException {
        String sql = """
                UPDATE feedback_attachments
                   SET status = ?, rejection_reason = ?, updated_at = ?
                 WHERE tenant_id = ? AND id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, attachment.status().name());
            statement.setString(2, attachment.rejectionReason());
            statement.setTimestamp(3, Timestamp.from(attachment.updatedAt()));
            statement.setObject(4, attachment.tenantId());
            statement.setObject(5, attachment.id());
            if (statement.executeUpdate() != 1) throw new IllegalStateException("Attachment disappeared during update");
        }
    }

    private void appendOutbox(Connection connection, FeedbackAttachment attachment, String eventType)
            throws SQLException {
        String payload = "{\"attachmentId\":\"" + attachment.id()
                + "\",\"documentId\":\"" + attachment.documentId()
                + "\",\"documentVersionId\":\"" + attachment.documentVersionId()
                + "\",\"status\":\"" + attachment.status().name() + "\"}";
        String sql = """
                INSERT INTO feedback_outbox
                    (event_id, tenant_id, aggregate_id, event_type, event_version, payload, occurred_at)
                VALUES (?, ?, ?, ?, 1, ?::jsonb, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, attachment.tenantId());
            statement.setObject(3, attachment.feedbackId().value());
            statement.setString(4, eventType);
            statement.setString(5, payload);
            statement.setTimestamp(6, Timestamp.from(attachment.updatedAt()));
            statement.executeUpdate();
        }
    }

    private static void verifyImmutableMetadata(Existing current, FeedbackAttachment attachment) {
        if (!current.feedbackId().equals(attachment.feedbackId().value())
                || !current.documentId().equals(attachment.documentId())
                || !current.documentVersionId().equals(attachment.documentVersionId())
                || !current.fileName().equals(attachment.fileName())
                || !current.mediaType().equals(attachment.mediaType())
                || current.sizeBytes() != attachment.sizeBytes()
                || !current.sha256().equalsIgnoreCase(attachment.sha256())) {
            throw new IllegalStateException("Attachment identity cannot be reused with different metadata");
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

    private record Existing(UUID feedbackId, String documentId, String documentVersionId,
                            String fileName, String mediaType, long sizeBytes, String sha256,
                            FeedbackAttachment.Status status, String rejectionReason, java.time.Instant updatedAt) {}
}
