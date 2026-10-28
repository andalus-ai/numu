package tech.kayys.numou.notification.persistence.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.notification.domain.Notification;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcNotificationRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcNotificationRepository repository;
    private final Instant now = Instant.parse("2026-10-28T02:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        executeMigration(dataSource);
        repository = new JdbcNotificationRepository(dataSource);
    }

    @Test
    void duplicateCreateReturnsCanonicalRecordWithoutDuplicateRows() {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        Notification first = notification(tenant, recipient, "invoice-1");
        Notification retry = notification(tenant, recipient, "invoice-1");

        Notification stored = repository.createIfAbsent(first);
        Notification duplicate = repository.createIfAbsent(retry);

        assertEquals(first.id(), stored.id());
        assertEquals(first.id(), duplicate.id());
        assertEquals(1, repository.findInbox(tenant, recipient, 100, null).size());
    }

    @Test
    void idempotencyIsScopedToTenantAndRecipient() {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();

        Notification original = repository.createIfAbsent(notification(tenant, recipient, "same-key"));
        Notification otherRecipient = repository.createIfAbsent(notification(tenant, UUID.randomUUID(), "same-key"));
        Notification otherTenant = repository.createIfAbsent(notification(UUID.randomUUID(), recipient, "same-key"));

        assertNotEquals(original.id(), otherRecipient.id());
        assertNotEquals(original.id(), otherTenant.id());
        assertEquals(1, repository.findInbox(tenant, recipient, 100, null).size());
    }

    @Test
    void recipientAndTenantScopePreventCrossScopeReadsAndMutations() {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        Notification stored = repository.createIfAbsent(notification(tenant, recipient, "scope-check"));

        assertTrue(repository.findById(tenant, UUID.randomUUID(), stored.id()).isEmpty());
        assertTrue(repository.findById(UUID.randomUUID(), recipient, stored.id()).isEmpty());
        assertTrue(repository.markRead(tenant, UUID.randomUUID(), stored.id(), now.plusSeconds(5)).isEmpty());
        assertFalse(repository.findById(tenant, recipient, stored.id()).orElseThrow().isRead());
    }

    @Test
    void markReadIsIdempotentAndKeepsFirstReadTimestamp() {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        Notification stored = repository.createIfAbsent(notification(tenant, recipient, "read-once"));

        Notification firstRead = repository.markRead(tenant, recipient, stored.id(), now.plusSeconds(5)).orElseThrow();
        Notification secondRead = repository.markRead(tenant, recipient, stored.id(), now.plusSeconds(20)).orElseThrow();

        assertEquals(now.plusSeconds(5), firstRead.readAt());
        assertEquals(firstRead.readAt(), secondRead.readAt());
    }

    private Notification notification(UUID tenant, UUID recipient, String key) {
        return Notification.create(tenant, recipient, "test.event", "Test notification",
                "Test body", "test:source", key, now);
    }

    private static void executeMigration(DataSource dataSource) throws Exception {
        String sql;
        try (var stream = JdbcNotificationRepositoryTest.class.getResourceAsStream("/db/migration/V1__notification_inbox.sql")) {
            if (stream == null) throw new IllegalStateException("Notification migration resource missing");
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read notification migration", e);
        }
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS notification_inbox CASCADE");
            for (String command : sql.split(";")) {
                if (!command.isBlank()) statement.execute(command);
            }
        }
    }
}