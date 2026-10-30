package tech.kayys.numou.notification.persistence.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.notification.application.port.PushTokenProtector;
import tech.kayys.numou.notification.domain.PushDevice;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Optional;
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


    @Test
    void managementSummariesDoNotSelectOrDecryptProviderTokens() {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        PushDevice stored = repository.registerOrRefresh(device(tenant, recipient, "secret-subscription-json", now));

        PushTokenProtector noRevealProtector = new PushTokenProtector() {
            @Override public EncryptedToken protect(UUID tenantId, UUID recipientId, String plaintextToken) {
                return protector().protect(tenantId, recipientId, plaintextToken);
            }
            @Override public String reveal(UUID tenantId, UUID recipientId, String keyId, String ciphertext) {
                throw new AssertionError("management projection must never decrypt provider tokens");
            }
        };
        JdbcPushDeviceRepository metadataRepository =
                new JdbcPushDeviceRepository(dataSource(), noRevealProtector);

        var summaries = metadataRepository.findActiveSummaries(tenant, recipient);

        assertEquals(1, summaries.size());
        assertEquals(stored.id(), summaries.getFirst().id());
        assertEquals(PushDevice.Platform.ANDROID, summaries.getFirst().platform());
        assertTrue(summaries.getFirst().active());
        assertFalse(summaries.getFirst().toString().contains("secret-subscription-json"));

        var disabled = metadataRepository.disableSummary(
                tenant, recipient, stored.id(), now.plusSeconds(10)).orElseThrow();
        assertFalse(disabled.active());
    }

    @Test
    void legacyTokenMigrationIsBoundedResumableAndRemovesPlaintext() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        String legacyToken = "legacy-provider-token";
        try (var connection = POSTGRES.createConnection("");
             PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO notification_push_device
                         (id, tenant_id, recipient_id, provider_token, provider_token_ciphertext,
                          provider_token_key_id, token_fingerprint, platform, app_instance,
                          created_at, updated_at, disabled_at)
                     VALUES (?, ?, ?, ?, NULL, NULL, ?, 'ANDROID', 'legacy-app', ?, ?, NULL)
                     """)) {
            insert.setObject(1, id);
            insert.setObject(2, tenant);
            insert.setObject(3, recipient);
            insert.setString(4, legacyToken);
            insert.setString(5, "a".repeat(64));
            insert.setTimestamp(6, java.sql.Timestamp.from(now));
            insert.setTimestamp(7, java.sql.Timestamp.from(now));
            assertEquals(1, insert.executeUpdate());
        }

        PGSimpleDataSource migrationDataSource = new PGSimpleDataSource();
        migrationDataSource.setURL(POSTGRES.getJdbcUrl());
        migrationDataSource.setUser(POSTGRES.getUsername());
        migrationDataSource.setPassword(POSTGRES.getPassword());
        JdbcLegacyPushTokenMigrator migrator =
                new JdbcLegacyPushTokenMigrator(migrationDataSource, protector());

        var result = migrator.migrateBatch(1);
        assertEquals(1, result.migratedInBatch());
        assertEquals(0, result.remainingLegacyRows());
        assertTrue(result.complete());
        var repeated = migrator.migrateBatch(1);
        assertEquals(0, repeated.migratedInBatch());
        assertTrue(repeated.complete());

        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT provider_token, provider_token_ciphertext, provider_token_key_id " +
                     "FROM notification_push_device WHERE id = '" + id + "'")) {
            assertTrue(rows.next());
            assertNull(rows.getString("provider_token"));
            assertEquals("test-key-v1", rows.getString("provider_token_key_id"));
            assertEquals(legacyToken, protector().reveal(
                    tenant, recipient, rows.getString("provider_token_key_id"),
                    rows.getString("provider_token_ciphertext")));
        }
    }

    private PGSimpleDataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
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
