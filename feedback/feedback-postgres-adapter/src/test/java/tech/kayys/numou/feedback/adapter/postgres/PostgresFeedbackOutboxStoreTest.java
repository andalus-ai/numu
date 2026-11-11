package tech.kayys.numou.feedback.adapter.postgres;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.feedback.application.outbox.FeedbackOutboxMessage;
import tech.kayys.numou.feedback.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresFeedbackOutboxStoreTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void claimsWithLeaseAndAllowsRecoveryAfterLeaseExpiry() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        Instant now = Instant.now();
        UUID eventId = createFeedbackEvent(dataSource, now.minusSeconds(2));
        PostgresFeedbackOutboxStore store = new PostgresFeedbackOutboxStore(dataSource);

        List<FeedbackOutboxMessage> first = store.claimPending(now, now.plusSeconds(30), 10);
        assertEquals(1, first.size());
        assertEquals(eventId, first.get(0).eventId());
        assertEquals(0, store.claimPending(now.plusSeconds(1), now.plusSeconds(31), 10).size());

        List<FeedbackOutboxMessage> recovered =
                store.claimPending(now.plusSeconds(31), now.plusSeconds(61), 10);
        assertEquals(1, recovered.size());
        assertEquals(eventId, recovered.get(0).eventId());

        store.markPublished(eventId, recovered.get(0).leaseToken(), now.plusSeconds(32));
        assertEquals(0, store.claimPending(now.plusSeconds(90), now.plusSeconds(120), 10).size());
        assertThrows(IllegalStateException.class, () -> store.markPublished(eventId, first.get(0).leaseToken(), now.plusSeconds(33)));
    }

    @Test
    void failedDeliveryIncrementsAttemptsAndDeadLettersAtConfiguredLimit() throws Exception {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        Instant now = Instant.now();
        UUID eventId = createFeedbackEvent(dataSource, now.minusSeconds(2));
        PostgresFeedbackOutboxStore store = new PostgresFeedbackOutboxStore(dataSource);

        List<FeedbackOutboxMessage> claimed = store.claimPending(now, now.plusSeconds(30), 10);
        assertEquals(1, claimed.size());
        store.recordFailure(eventId, claimed.get(0).leaseToken(), "broker unavailable", now.plusSeconds(10), 1);

        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT attempts, last_error, dead_lettered_at, lease_until
                       FROM feedback_outbox WHERE event_id = ?
                     """)) {
            statement.setObject(1, eventId);
            try (ResultSet row = statement.executeQuery()) {
                assertTrue(row.next());
                assertEquals(1, row.getInt("attempts"));
                assertEquals("broker unavailable", row.getString("last_error"));
                assertNotNull(row.getTimestamp("dead_lettered_at"));
                assertNull(row.getTimestamp("lease_until"));
            }
        }
        assertEquals(0, store.claimPending(now.plusSeconds(60), now.plusSeconds(90), 10).size());
    }

    private UUID createFeedbackEvent(DataSource dataSource, Instant at) {
        UUID tenant = UUID.randomUUID();
        Feedback feedback = Feedback.submit(FeedbackId.generate(), TenantId.of(tenant),
                new FeedbackActor("user", "integration-test"), FeedbackKind.BUG,
                FeedbackPriority.NORMAL, "Outbox test", "A test feedback event",
                FeedbackContext.empty(), at);
        new PostgresFeedbackRepository(dataSource).save(feedback, UUID.randomUUID().toString());
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT event_id FROM feedback_outbox ORDER BY created_at DESC LIMIT 1")) {
            if (!row.next()) throw new IllegalStateException("Feedback submission did not create an outbox event");
            return row.getObject(1, UUID.class);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
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
                "V1__feedback_foundation.sql",
                "V2__feedback_attachments.sql",
                "V3__feedback_outbox_leases_and_dead_letters.sql"
        }) {
            String sql = Files.readString(Path.of("src/main/resources/db/migration/" + migration));
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                for (String part : sql.split(";")) if (!part.isBlank()) statement.execute(part);
            }
        }
    }
}
