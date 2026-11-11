package tech.kayys.numou.feedback.adapter.postgres;

import org.junit.jupiter.api.BeforeAll;
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
        TenantId tenantId = TenantId.generate();
        Instant now = Instant.parse("2026-11-11T04:00:00Z");
        Feedback feedback = Feedback.submit(FeedbackId.generate(), tenantId,
                new FeedbackActor("user", UUID.randomUUID().toString()),
                FeedbackKind.BUG, FeedbackPriority.HIGH, "Checkout error",
                "Checkout fails on submit.",
                new FeedbackContext("pos", "checkout", "submit", "order", "order:1", "1.0.0"), now);

        FeedbackId first = repository.save(feedback, "submit-001");
        Feedback retry = Feedback.submit(FeedbackId.generate(), tenantId,
                new FeedbackActor("user", "different-actor"), FeedbackKind.BUG,
                FeedbackPriority.HIGH, "Different retry payload",
                "This must not create a second item.", FeedbackContext.empty(), now);
        FeedbackId second = repository.save(retry, "submit-001");

        assertEquals(first, second);
        Feedback loaded = repository.findById(tenantId, first).orElseThrow();
        assertEquals(FeedbackStatus.SUBMITTED, loaded.status());
        assertEquals(FeedbackKind.BUG, loaded.kind());
        assertEquals("order:1", loaded.context().recordReference());
        assertTrue(loaded.getDomainEvents().isEmpty());

        try (Connection c = dataSource.getConnection();
             var s = c.prepareStatement("""
                     SELECT (SELECT count(*) FROM feedback_items WHERE tenant_id = ?),
                            (SELECT count(*) FROM feedback_idempotency WHERE tenant_id = ?),
                            (SELECT count(*) FROM feedback_outbox WHERE tenant_id = ?)
                     """)) {
            s.setObject(1, tenantId.value());
            s.setObject(2, tenantId.value());
            s.setObject(3, tenantId.value());
            try (var rows = s.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
                assertEquals(1, rows.getInt(2));
                assertEquals(1, rows.getInt(3));
            }
        }
        assertTrue(repository.findById(TenantId.generate(), first).isEmpty());
    }

    @Test
    void idempotencyKeysAreScopedToTenant() {
        DataSource dataSource = dataSource();
        PostgresFeedbackRepository repository = new PostgresFeedbackRepository(dataSource);
        Instant now = Instant.parse("2026-11-11T04:00:00Z");
        TenantId tenantA = TenantId.generate();
        TenantId tenantB = TenantId.generate();

        Feedback first = Feedback.submit(FeedbackId.generate(), tenantA,
                new FeedbackActor("user", "a"), FeedbackKind.COMMENT, FeedbackPriority.NORMAL,
                "A", "Tenant A feedback", FeedbackContext.empty(), now);
        Feedback second = Feedback.submit(FeedbackId.generate(), tenantB,
                new FeedbackActor("user", "b"), FeedbackKind.COMMENT, FeedbackPriority.NORMAL,
                "B", "Tenant B feedback", FeedbackContext.empty(), now);

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
