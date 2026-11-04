package tech.kayys.numou.security.vault.postgres;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.security.vault.domain.SecretId;
import tech.kayys.numou.security.vault.domain.VaultId;
import tech.kayys.numou.security.vault.spi.VaultAuditEntry;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresSecretRepositoryAdapterTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DataSource dataSource;
    private PostgresSecretRepositoryAdapter repository;

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        dataSource = source;
        repository = new PostgresSecretRepositoryAdapter(dataSource);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS vault_secret_lifecycle");
            statement.execute("DROP TABLE IF EXISTS vault_audit_outbox");
            statement.execute("DROP TABLE IF EXISTS vault_secret_version");
            try (InputStream migration = getClass().getResourceAsStream(
                    "/db/migration/V1__vault_secret_versions_and_audit_outbox.sql")) {
                assertNotNull(migration);
                String[] commands = new String(migration.readAllBytes(), StandardCharsets.UTF_8).split(";");
                for (String command : commands) {
                    if (!command.isBlank()) statement.execute(command);
                }
            }
            try (InputStream migration = getClass().getResourceAsStream(
                    "/db/migration/V2__vault_audit_outbox_leases.sql")) {
                assertNotNull(migration);
                String[] commands = new String(migration.readAllBytes(), StandardCharsets.UTF_8).split(";");
                for (String command : commands) {
                    if (!command.isBlank()) statement.execute(command);
                }
            }
        }
    }

    @Test
    void appendsEncryptedVersionAndAuditAtomicallyAndScopesReadsByTenant() throws Exception {
        UUID tenant = UUID.randomUUID();
        VaultId vault = VaultId.generate();
        SecretId secret = SecretId.generate();
        byte[] ciphertext = new byte[]{9, 8, 7, 6};
        var saved = repository.appendVersion(tenant, vault, secret, 0, 1,
                "AES-256-GCM-ENVELOPE", 1, "kms/key/v1", ciphertext, null,
                new VaultAuditEntry("operator", "VAULT_SECRET_CREATED", "version=1", Instant.now()))
                .await().indefinitely();

        assertArrayEquals(ciphertext, saved.ciphertext());
        assertTrue(repository.findVersion(UUID.randomUUID(), vault, secret, 1)
                .await().indefinitely().isEmpty());
        assertEquals(1, repository.findLatest(tenant, vault, secret).await().indefinitely()
                .orElseThrow().version().value());
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT count(*) FROM vault_audit_outbox")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT encode(ciphertext, 'hex') FROM vault_secret_version")) {
            assertTrue(result.next());
            assertEquals("09080706", result.getString(1));
        }
    }


    @Test
    void revocationAndAuditAreAtomicAndRevocationIsIdempotent() throws Exception {
        UUID tenant = UUID.randomUUID();
        VaultId vault = VaultId.generate();
        SecretId secret = SecretId.generate();
        repository.appendVersion(tenant, vault, secret, 0, 1, "AES-256-GCM-ENVELOPE", 1,
                "kms/key/v1", new byte[]{4, 5}, null,
                new VaultAuditEntry("operator", "VAULT_SECRET_CREATED", "version=1", Instant.now()))
                .await().indefinitely();

        PostgresSecretLifecycleAdapter lifecycle = new PostgresSecretLifecycleAdapter(dataSource);
        VaultAuditEntry revoked = new VaultAuditEntry("operator", "VAULT_SECRET_REVOKED", "revoked=true", Instant.now());
        lifecycle.revoke(tenant, vault, secret, revoked).await().indefinitely();
        lifecycle.revoke(tenant, vault, secret, revoked).await().indefinitely();

        assertEquals(tech.kayys.numou.security.vault.domain.SecretStatus.REVOKED,
                lifecycle.status(tenant, vault, secret).await().indefinitely().orElseThrow());
        assertThrows(RuntimeException.class, () -> repository.appendVersion(tenant, vault, secret, 1, 2,
                "AES-256-GCM-ENVELOPE", 1, "kms/key/v1", new byte[]{6, 7}, null,
                new VaultAuditEntry("operator", "VAULT_SECRET_ROTATED", "version=2", Instant.now()))
                .await().indefinitely());
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT count(*) FROM vault_audit_outbox WHERE event_type = 'VAULT_SECRET_REVOKED'")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1), "repeated revocation must not duplicate audit facts");
        }
    }


    @Test
    void repeatedRevealAuditsAreStoredAndOutboxClaimsAreLeased() throws Exception {
        UUID tenant = UUID.randomUUID();
        VaultId vault = VaultId.generate();
        SecretId secret = SecretId.generate();
        repository.appendVersion(tenant, vault, secret, 0, 1, "AES-256-GCM-ENVELOPE", 1,
                "kms/key/v1", new byte[]{1, 2}, null,
                new VaultAuditEntry("operator", "VAULT_SECRET_CREATED", "version=1", Instant.now()))
                .await().indefinitely();

        VaultAuditEntry reveal = new VaultAuditEntry("operator", "VAULT_SECRET_REVEALED", "version=1", Instant.now());
        repository.appendAuditEvent(tenant, vault, secret, 1, reveal).await().indefinitely();
        repository.appendAuditEvent(tenant, vault, secret, 1, reveal).await().indefinitely();

        PostgresVaultAuditOutboxAdapter outbox = new PostgresVaultAuditOutboxAdapter(dataSource);
        Instant now = Instant.now().plusSeconds(1);
        var firstClaim = outbox.claimDue("worker-a", now, now.plusSeconds(30), 10).await().indefinitely();
        assertEquals(3, firstClaim.size());
        assertEquals(2, firstClaim.stream().filter(e -> e.eventType().equals("VAULT_SECRET_REVEALED")).count());

        assertTrue(outbox.claimDue("worker-b", now, now.plusSeconds(30), 10)
                .await().indefinitely().isEmpty());
        var first = firstClaim.get(0);
        outbox.markPublished(first.eventId(), "worker-a", now).await().indefinitely();
        assertThrows(RuntimeException.class, () -> outbox.markPublished(first.eventId(), "worker-b", now)
                .await().indefinitely());

        var reclaimed = outbox.claimDue("worker-b", now.plusSeconds(31),
                now.plusSeconds(61), 10).await().indefinitely();
        assertEquals(2, reclaimed.size());
        assertTrue(reclaimed.stream().allMatch(e -> e.attempts() == 2));
    }

    @Test
    void outboxRetryCanBeDeadLetteredAndReleasesLease() {
        UUID tenant = UUID.randomUUID();
        VaultId vault = VaultId.generate();
        SecretId secret = SecretId.generate();
        repository.appendVersion(tenant, vault, secret, 0, 1, "AES-256-GCM-ENVELOPE", 1,
                "kms/key/v1", new byte[]{1, 2}, null,
                new VaultAuditEntry("operator", "VAULT_SECRET_CREATED", "version=1", Instant.now()))
                .await().indefinitely();
        PostgresVaultAuditOutboxAdapter outbox = new PostgresVaultAuditOutboxAdapter(dataSource);
        Instant now = Instant.now().plusSeconds(1);
        var claimed = outbox.claimDue("worker-a", now, now.plusSeconds(30), 1).await().indefinitely();
        var event = claimed.get(0);
        outbox.reschedule(event.eventId(), "worker-a", now.plusSeconds(60),
                "PublishFailure", true).await().indefinitely();
        assertTrue(outbox.claimDue("worker-b", now.plusSeconds(120),
                now.plusSeconds(150), 10).await().indefinitely().isEmpty());
    }

    @Test
    void rollsBackSecretVersionWhenAuditOutboxInsertFails() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE OR REPLACE FUNCTION reject_vault_audit_insert() RETURNS trigger
                    LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''simulated audit storage failure''; END;'
                    """);
            statement.execute("""
                    CREATE TRIGGER reject_vault_audit BEFORE INSERT ON vault_audit_outbox
                    FOR EACH ROW EXECUTE FUNCTION reject_vault_audit_insert()
                    """);
        }

        UUID tenant = UUID.randomUUID();
        VaultId vault = VaultId.generate();
        SecretId secret = SecretId.generate();
        assertThrows(RuntimeException.class, () -> repository.appendVersion(tenant, vault, secret, 0, 1,
                "AES-256-GCM-ENVELOPE", 1, "kms/key/v1", new byte[]{1, 2}, null,
                new VaultAuditEntry("operator", "VAULT_SECRET_CREATED", "version=1", Instant.now()))
                .await().indefinitely());

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT count(*) FROM vault_secret_version")) {
            assertTrue(result.next());
            assertEquals(0, result.getInt(1), "secret version must roll back with failed audit insert");
        }
    }

    @Test
    void rejectsStaleVersionAndDoesNotWriteSecondAuditEvent() throws Exception {
        UUID tenant = UUID.randomUUID();
        VaultId vault = VaultId.generate();
        SecretId secret = SecretId.generate();
        repository.appendVersion(tenant, vault, secret, 0, 1, "AES-256-GCM-ENVELOPE", 1,
                "kms/key/v1", new byte[]{1, 2}, null,
                new VaultAuditEntry("operator", "VAULT_SECRET_CREATED", "version=1", Instant.now()))
                .await().indefinitely();

        assertThrows(RuntimeException.class, () -> repository.appendVersion(tenant, vault, secret, 0, 1,
                "AES-256-GCM-ENVELOPE", 1, "kms/key/v1", new byte[]{3, 4}, null,
                new VaultAuditEntry("operator", "VAULT_SECRET_CREATED", "version=1", Instant.now()))
                .await().indefinitely());
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT count(*) FROM vault_audit_outbox")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }
}
