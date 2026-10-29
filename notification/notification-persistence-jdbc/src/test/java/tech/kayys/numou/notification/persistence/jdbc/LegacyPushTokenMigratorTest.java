package tech.kayys.numou.notification.persistence.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.notification.application.port.PushTokenProtector;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class LegacyPushTokenMigratorTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DataSource dataSource;
    private AesGcmPushTokenProtector protector;
    private final Instant now = Instant.parse("2026-10-30T00:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        dataSource = source;
        executeMigrations(source);
        protector = new AesGcmPushTokenProtector(keys());
    }

    @Test
    void migratesOnlyBoundedBatchAndCanResumeUntilNoPlaintextRemains() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        insertLegacy(tenant, recipient, "legacy-token-one");
        insertLegacy(tenant, recipient, "legacy-token-two");
        insertLegacy(tenant, recipient, "legacy-token-three");

        LegacyPushTokenMigrator migrator = migrator(protector);
        var first = migrator.migrateBatch(2);
        assertEquals(2, first.scanned());
        assertEquals(2, first.migrated());
        assertEquals(1, first.remaining());
        assertFalse(first.complete());

        var second = migrator.migrateBatch(2);
        assertEquals(1, second.scanned());
        assertEquals(1, second.migrated());
        assertEquals(0, second.remaining());
        assertTrue(second.complete());

        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT COUNT(*) FILTER (WHERE provider_token IS NOT NULL) AS plaintext,
                            COUNT(*) FILTER (WHERE provider_token_ciphertext IS NOT NULL
                                              AND provider_token_key_id IS NOT NULL) AS encrypted
                     FROM notification_push_device
                     """)) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt("plaintext"));
            assertEquals(3, rows.getInt("encrypted"));
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT tenant_id, recipient_id, provider_token, provider_token_ciphertext, provider_token_key_id
                     FROM notification_push_device
                     """)) {
            Set<String> decrypted = new java.util.HashSet<>();
            while (rows.next()) {
                assertNull(rows.getString("provider_token"));
                decrypted.add(protector.reveal(rows.getObject("tenant_id", UUID.class),
                        rows.getObject("recipient_id", UUID.class),
                        rows.getString("provider_token_key_id"),
                        rows.getString("provider_token_ciphertext")));
            }
            assertEquals(Set.of("legacy-token-one", "legacy-token-two", "legacy-token-three"), decrypted);
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT COUNT(*) AS batches, SUM(migrated_count) AS migrated,
                            MAX(remaining_count) FILTER (WHERE outcome = 'SUCCEEDED') AS max_remaining
                     FROM notification_push_token_migration_audit
                     """)) {
            assertTrue(rows.next());
            assertEquals(2, rows.getInt("batches"));
            assertEquals(3, rows.getInt("migrated"));
            assertEquals(1, rows.getLong("max_remaining"));
        }
    }

    @Test
    void failedBatchRollsBackAndWritesCountOnlyFailureAudit() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        insertLegacy(tenant, recipient, "legacy-token-failure");

        PushTokenProtector failing = new PushTokenProtector() {
            public EncryptedToken protect(UUID t, UUID r, String token) {
                throw new IllegalStateException("simulated key service outage");
            }
            public String reveal(UUID t, UUID r, String keyId, String ciphertext) {
                throw new UnsupportedOperationException();
            }
        };
        assertThrows(IllegalStateException.class, () -> migrator(failing).migrateBatch(10));

        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT provider_token, provider_token_ciphertext FROM notification_push_device
                     """)) {
            assertTrue(rows.next());
            assertEquals("legacy-token-failure", rows.getString("provider_token"));
            assertNull(rows.getString("provider_token_ciphertext"));
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT outcome, failure_code, scanned_count, migrated_count, remaining_count
                     FROM notification_push_token_migration_audit
                     """)) {
            assertTrue(rows.next());
            assertEquals("FAILED", rows.getString("outcome"));
            assertEquals("BATCH_FAILED", rows.getString("failure_code"));
            assertEquals(1, rows.getInt("scanned_count"));
            assertEquals(0, rows.getInt("migrated_count"));
            assertEquals(1, rows.getLong("remaining_count"));
        }
    }

    @Test
    void rejectsUnboundedOrInvalidBatchSizes() {
        LegacyPushTokenMigrator migrator = migrator(protector);
        assertThrows(IllegalArgumentException.class, () -> migrator.migrateBatch(0));
        assertThrows(IllegalArgumentException.class, () -> migrator.migrateBatch(1_001));
    }

    private LegacyPushTokenMigrator migrator(PushTokenProtector tokenProtector) {
        return new LegacyPushTokenMigrator(dataSource, tokenProtector,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private void insertLegacy(UUID tenant, UUID recipient, String token) throws Exception {
        String fingerprint = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(token.getBytes(StandardCharsets.UTF_8)));
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO notification_push_device
                       (id, tenant_id, recipient_id, provider_token, token_fingerprint,
                        platform, app_instance, created_at, updated_at, disabled_at)
                     VALUES (?, ?, ?, ?, ?, 'ANDROID', 'test-app', ?, ?, NULL)
                     """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, tenant);
            statement.setObject(3, recipient);
            statement.setString(4, token);
            statement.setString(5, fingerprint);
            statement.setObject(6, java.sql.Timestamp.from(now));
            statement.setObject(7, java.sql.Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private PushTokenKeyProvider keys() {
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        return new PushTokenKeyProvider() {
            public KeyMaterial activeKey() { return new KeyMaterial("test-key-v1", key); }
            public Optional<SecretKey> keyFor(String keyId) {
                return "test-key-v1".equals(keyId) ? Optional.of(key) : Optional.empty();
            }
        };
    }

    private static void executeMigrations(DataSource source) throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS notification_push_token_migration_audit CASCADE");
            statement.execute("DROP TABLE IF EXISTS notification_delivery_job CASCADE");
            statement.execute("DROP TABLE IF EXISTS notification_push_device CASCADE");
            statement.execute("DROP TABLE IF EXISTS notification_inbox CASCADE");
        }
        for (String migration : List.of(
                "V1__notification_inbox.sql",
                "V2__notification_push_device.sql",
                "V3__notification_delivery_jobs.sql",
                "V4__notification_push_token_encryption.sql",
                "V5__notification_push_token_migration_audit.sql")) {
            String sql;
            try (var stream = LegacyPushTokenMigratorTest.class
                    .getResourceAsStream("/db/migration/" + migration)) {
                if (stream == null) throw new IllegalStateException("Migration missing: " + migration);
                sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                for (String command : sql.split(";")) if (!command.isBlank()) statement.execute(command);
            }
        }
    }
}
