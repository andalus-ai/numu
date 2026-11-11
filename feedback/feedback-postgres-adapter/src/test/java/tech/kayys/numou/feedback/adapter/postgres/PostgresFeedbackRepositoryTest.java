package tech.kayys.numou.feedback.adapter.postgres;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.feedback.domain.*;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresFeedbackRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void setUpSchema() throws Exception {
        migrate(dataSource());
    }

    @Test
    void savesAggregateOutboxAndIdempotencyAtomically() throws Exception {
        DataSource dataSource = dataSource();
        PostgresFeedbackRepository repository = new PostgresFeedbackRepository(dataSource);
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-11T04:00:00Z");
        FeedbackItem item = FeedbackItem.submit(FeedbackId.generate(), tenantId, null,
                FeedbackType.BUG, "Checkout error", "Checkout fails on submit.",
                "pos", "checkout", "order:1", FeedbackPriority.HIGH, now);

        FeedbackId first = repository.save(item, "submit-001");
        FeedbackItem retry = FeedbackItem.submit(FeedbackId.generate(), tenantId, null,
                FeedbackType.BUG, "Different retry payload", "This must not create a second item.",
                "pos", "checkout", "order:1", FeedbackPriority.HIGH, now);
        FeedbackId second = repository.save(retry, "submit-001");

        assertEquals(first, second);
        assertEquals(FeedbackStatus.SUBMITTED,
                repository.findById(tenantId, first).orElseThrow().status());
        try (Connection c = dataSource.getConnection();
             var s = c.prepareStatement("""
                     SELECT (SELECT count(*) FROM feedback_items WHERE tenant_id = ?),
                            (SELECT count(*) FROM feedback_idempotency WHERE tenant_id = ?),
                            (SELECT count(*) FROM feedback_outbox WHERE tenant_id = ?)
                     """)) {
            s.setObject(1, tenantId);
            s.setObject(2, tenantId);
            s.setObject(3, tenantId);
            try (var rows = s.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
                assertEquals(1, rows.getInt(2));
                assertEquals(1, rows.getInt(3));
            }
        }
        assertTrue(repository.findById(UUID.randomUUID(), first).isEmpty());
    }

    @Test
    void idempotencyKeysAreScopedToTenant() throws Exception {
        DataSource dataSource = dataSource();
        PostgresFeedbackRepository repository = new PostgresFeedbackRepository(dataSource);
        Instant now = Instant.parse("2026-11-11T04:00:00Z");
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        FeedbackItem first = FeedbackItem.submit(FeedbackId.generate(), tenantA, null,
                FeedbackType.COMMENT, "A", "Tenant A feedback", null, null, null,
                FeedbackPriority.NORMAL, now);
        FeedbackItem second = FeedbackItem.submit(FeedbackId.generate(), tenantB, null,
                FeedbackType.COMMENT, "B", "Tenant B feedback", null, null, null,
                FeedbackPriority.NORMAL, now);

        FeedbackId a = repository.save(first, "same-key");
        FeedbackId b = repository.save(second, "same-key");

        assertNotEquals(a, b);
        assertTrue(repository.findById(tenantA, b).isEmpty());
        assertTrue(repository.findById(tenantB, a).isEmpty());
    }

    private static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    private static void migrate(DataSource dataSource) throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V1__feedback_foundation.sql"));
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            for (String statement : sql.split(";")) {
                if (!statement.isBlank()) s.execute(statement);
            }
        }
    }
}