package tech.kayys.numou.notification.persistence.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.notification.domain.PushDevice;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcPushDeviceRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcPushDeviceRepository repository;
    private final Instant now = Instant.parse("2026-10-28T02:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        executeMigration(dataSource);
        repository = new JdbcPushDeviceRepository(dataSource);
    }

    @Test
    void sameOwnerRegistrationRefreshesExistingDevice() {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();

        PushDevice first = repository.registerOrRefresh(device(tenant, recipient, "same-token", now));
        PushDevice refreshed = repository.registerOrRefresh(device(
                tenant, recipient, "same-token", now.plusSeconds(30)));

        assertEquals(first.id(), refreshed.id());
        assertEquals(now, refreshed.createdAt());
        assertEquals(now.plusSeconds(30), refreshed.updatedAt());
        assertEquals(1, repository.findActiveByRecipient(tenant, recipient).size());
    }

    @Test
    void tokenCannotBeReassignedAcrossTenantOrRecipient() {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        repository.registerOrRefresh(device(tenant, recipient, "private-token", now));

        assertThrows(IllegalStateException.class, () -> repository.registerOrRefresh(
                device(tenant, UUID.randomUUID(), "private-token", now.plusSeconds(10))));
        assertThrows(IllegalStateException.class, () -> repository.registerOrRefresh(
                device(UUID.randomUUID(), recipient, "private-token", now.plusSeconds(10))));
        assertEquals(1, repository.findActiveByRecipient(tenant, recipient).size());
    }

    @Test
    void disableIsScopedAndReregistrationReactivatesExistingDevice() {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        PushDevice stored = repository.registerOrRefresh(device(tenant, recipient, "reusable-token", now));

        assertTrue(repository.disable(tenant, UUID.randomUUID(), stored.id(), now.plusSeconds(10)).isEmpty());
        PushDevice disabled = repository.disable(tenant, recipient, stored.id(), now.plusSeconds(10)).orElseThrow();
        assertFalse(disabled.isActive());
        assertTrue(repository.findActiveByRecipient(tenant, recipient).isEmpty());

        PushDevice reactivated = repository.registerOrRefresh(device(
                tenant, recipient, "reusable-token", now.plusSeconds(20)));
        assertEquals(stored.id(), reactivated.id());
        assertTrue(reactivated.isActive());
        assertEquals(1, repository.findActiveByRecipient(tenant, recipient).size());
    }

    @Test
    void activeDeviceListingIsTenantAndRecipientScoped() {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        repository.registerOrRefresh(device(tenant, recipient, "token-a", now));
        repository.registerOrRefresh(device(tenant, UUID.randomUUID(), "token-b", now));
        repository.registerOrRefresh(device(UUID.randomUUID(), recipient, "token-c", now));

        assertEquals(1, repository.findActiveByRecipient(tenant, recipient).size());
        assertTrue(repository.findActiveByRecipient(UUID.randomUUID(), recipient).isEmpty());
    }

    private PushDevice device(UUID tenant, UUID recipient, String token, Instant time) {
        return PushDevice.register(tenant, recipient, token, PushDevice.Platform.ANDROID, "test-app", time);
    }

    private static void executeMigration(DataSource dataSource) throws Exception {
        String sql;
        try (var stream = JdbcPushDeviceRepositoryTest.class
                .getResourceAsStream("/db/migration/V2__notification_push_device.sql")) {
            if (stream == null) throw new IllegalStateException("Push device migration resource missing");
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS notification_push_device CASCADE");
            for (String command : sql.split(";")) {
                if (!command.isBlank()) statement.execute(command);
            }
        }
    }
}
