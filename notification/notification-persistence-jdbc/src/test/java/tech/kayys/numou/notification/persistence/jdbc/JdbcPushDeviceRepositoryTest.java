package tech.kayys.numou.notification.persistence.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.notification.domain.PushDevice;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.sql.ResultSet;
import java.util.Optional;

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
        repository = new JdbcPushDeviceRepository(dataSource, protector());
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
    void providerTokenIsEncryptedAtRestAndRoundTripsForOwner() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        PushDevice saved = repository.registerOrRefresh(device(tenant, recipient, "high-entropy-token", now));

        try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT provider_token, provider_token_ciphertext, provider_token_key_id FROM notification_push_device WHERE id = '" + saved.id() + "'")) {
            assertTrue(rows.next());
            assertNull(rows.getString("provider_token"));
            assertNotEquals("high-entropy-token", rows.getString("provider_token_ciphertext"));
            assertEquals("test-key-v1", rows.getString("provider_token_key_id"));
        }
        assertEquals("high-entropy-token",
                repository.findActiveByRecipient(tenant, recipient).getFirst().token());
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

    private PushTokenProtector protector() {
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        PushTokenKeyProvider keys = new PushTokenKeyProvider() {
            public KeyMaterial activeKey() { return new KeyMaterial("test-key-v1", key); }
            public Optional<SecretKey> keyFor(String keyId) {
                return "test-key-v1".equals(keyId) ? Optional.of(key) : Optional.empty();
            }
        };
        return new AesGcmPushTokenProtector(keys);
    }

    private static void executeMigration(DataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS notification_push_device CASCADE");
        }
        for (String resource : new String[]{
                "/db/migration/V2__notification_push_device.sql",
                "/db/migration/V4__notification_push_token_encryption.sql"}) {
            String sql;
            try (var stream = JdbcPushDeviceRepositoryTest.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IllegalStateException("Migration resource missing: " + resource);
                sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                for (String command : sql.split(";")) {
                    if (!command.isBlank()) statement.execute(command);
                }
            }
        }
    }
}
