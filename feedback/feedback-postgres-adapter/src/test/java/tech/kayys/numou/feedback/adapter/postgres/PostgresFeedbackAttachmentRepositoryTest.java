package tech.kayys.numou.feedback.adapter.postgres;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.feedback.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresFeedbackAttachmentRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void persistsAndReloadsTenantScopedAttachmentLifecycle() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        PostgresFeedbackRepository feedbacks = new PostgresFeedbackRepository(dataSource);
        PostgresFeedbackAttachmentRepository attachments = new PostgresFeedbackAttachmentRepository(dataSource);
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-11T04:00:00Z");
        Feedback feedback = feedback(tenantId, now, "attachment-test-feedback");
        feedbacks.save(feedback, "attachment-test-feedback");

        FeedbackAttachment attachment = FeedbackAttachment.register(
                UUID.randomUUID(), tenantId, feedback.id(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), "screenshot.png", "image/png",
                1024, "c".repeat(64), now);
        attachments.save(attachment);

        assertEquals(FeedbackAttachment.Status.PENDING_SCAN,
                attachments.findById(tenantId, attachment.id()).orElseThrow().status());
        assertEquals(1, attachments.findByFeedback(tenantId, feedback.id().value()).size());
        assertTrue(attachments.findById(UUID.randomUUID(), attachment.id()).isEmpty());
        assertTrue(attachments.findByFeedback(UUID.randomUUID(), feedback.id().value()).isEmpty());

        attachment.markAvailable(now.plusSeconds(5));
        attachments.save(attachment);
        var reloaded = attachments.findById(tenantId, attachment.id()).orElseThrow();
        assertEquals(FeedbackAttachment.Status.AVAILABLE, reloaded.status());
        assertEquals(attachment.documentId(), reloaded.documentId());
        assertEquals(attachment.documentVersionId(), reloaded.documentVersionId());

        FeedbackAttachment staleSameTimestamp = FeedbackAttachment.restore(
                attachment.id(), tenantId, feedback.id(), attachment.documentId(), attachment.documentVersionId(),
                attachment.fileName(), attachment.mediaType(), attachment.sizeBytes(), attachment.sha256(),
                FeedbackAttachment.Status.PENDING_SCAN, now, now.plusSeconds(5), null);
        assertThrows(IllegalStateException.class, () -> attachments.save(staleSameTimestamp));

        // One submission event + attachment-added + attachment-status-changed.
        assertEquals(3, countOutbox(dataSource));
        attachments.save(reloaded); // idempotent replay must not emit another event
        assertEquals(3, countOutbox(dataSource));
    }

    @Test
    void databaseRejectsCrossTenantFeedbackReferences() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        PostgresFeedbackRepository feedbacks = new PostgresFeedbackRepository(dataSource);
        PostgresFeedbackAttachmentRepository attachments = new PostgresFeedbackAttachmentRepository(dataSource);
        UUID ownerTenant = UUID.randomUUID();
        UUID wrongTenant = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-11T04:00:00Z");
        Feedback feedback = feedback(ownerTenant, now, "cross-tenant-feedback");
        feedbacks.save(feedback, "cross-tenant-feedback");
        FeedbackAttachment forged = FeedbackAttachment.register(
                UUID.randomUUID(), wrongTenant, feedback.id(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), "proof.pdf", "application/pdf",
                50, "d".repeat(64), now);

        assertThrows(FeedbackPersistenceException.class, () -> attachments.save(forged));
    }

    private Feedback feedback(UUID tenantId, Instant now, String actorId) {
        return Feedback.submit(FeedbackId.generate(), TenantId.of(tenantId),
                new FeedbackActor("user", actorId), FeedbackKind.BUG, FeedbackPriority.NORMAL,
                "Broken upload", "The screenshot upload fails.", FeedbackContext.empty(), now);
    }

    private int countOutbox(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM feedback_outbox")) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    private void migrate(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
        for (String migration : new String[] {
                "V1__feedback_foundation.sql", "V2__feedback_attachments.sql"
        }) {
            String sql = Files.readString(Path.of("src/main/resources/db/migration/" + migration));
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                for (String part : sql.split(";")) if (!part.isBlank()) statement.execute(part);
            }
        }
    }
}
