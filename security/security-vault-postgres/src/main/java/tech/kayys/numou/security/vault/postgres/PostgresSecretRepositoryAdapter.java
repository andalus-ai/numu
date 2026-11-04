package tech.kayys.numou.security.vault.postgres;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import tech.kayys.numou.security.vault.domain.*;
import tech.kayys.numou.security.vault.spi.SecretRepositoryPort;
import tech.kayys.numou.security.vault.spi.VaultAuditEntry;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC adapter. Blocking JDBC work is dispatched to Mutiny's worker pool.
 * Secret version and its audit/outbox fact are inserted in one transaction.
 */
public final class PostgresSecretRepositoryAdapter implements SecretRepositoryPort {
    private final DataSource dataSource;

    public PostgresSecretRepositoryAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    @Override
    public Uni<EncryptedSecret> appendVersion(UUID tenantId, VaultId vaultId, SecretId secretId,
            int expectedPreviousVersion, int newVersion, String algorithm, int formatVersion,
            String keyId, byte[] ciphertext, Instant expiresAt, VaultAuditEntry auditEntry) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(vaultId, "vaultId");
        Objects.requireNonNull(secretId, "secretId");
        Objects.requireNonNull(ciphertext, "ciphertext");
        Objects.requireNonNull(auditEntry, "auditEntry");
        if (expectedPreviousVersion < 0 || newVersion != expectedPreviousVersion + 1)
            return Uni.createFrom().failure(new IllegalArgumentException("new version must follow expected previous version"));
        byte[] encryptedCopy = ciphertext.clone();
        return Uni.createFrom().item(() -> appendBlocking(tenantId, vaultId, secretId,
                expectedPreviousVersion, newVersion, algorithm, formatVersion, keyId,
                encryptedCopy, expiresAt, auditEntry))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private EncryptedSecret appendBlocking(UUID tenantId, VaultId vaultId, SecretId secretId,
            int expectedPreviousVersion, int newVersion, String algorithm, int formatVersion,
            String keyId, byte[] ciphertext, Instant expiresAt, VaultAuditEntry auditEntry) {
        Instant createdAt = Instant.now();
        try (Connection connection = dataSource.getConnection()) {
            if (expiresAt != null && !expiresAt.isAfter(createdAt))
                throw new IllegalArgumentException("expiresAt must be after createdAt");
            connection.setAutoCommit(false);
            try {
                int actual = lockAndReadLatestVersion(connection, tenantId, vaultId, secretId);
                if (actual != expectedPreviousVersion)
                    throw new SecretVersionConflictException(expectedPreviousVersion, actual);
                if (expectedPreviousVersion > 0 && isRevoked(connection, tenantId, vaultId, secretId))
                    throw new IllegalStateException("revoked secret cannot be rotated");

                String insertSecret = """
                        INSERT INTO vault_secret_version
                            (tenant_id, vault_id, secret_id, secret_version, encryption_algorithm,
                             format_version, key_id, ciphertext, created_at, expires_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement statement = connection.prepareStatement(insertSecret)) {
                    statement.setObject(1, tenantId);
                    statement.setObject(2, vaultId.value());
                    statement.setObject(3, secretId.value());
                    statement.setInt(4, newVersion);
                    statement.setString(5, algorithm);
                    statement.setInt(6, formatVersion);
                    statement.setString(7, keyId);
                    statement.setBytes(8, ciphertext);
                    statement.setTimestamp(9, Timestamp.from(createdAt));
                    if (expiresAt == null) statement.setNull(10, Types.TIMESTAMP_WITH_TIMEZONE);
                    else statement.setTimestamp(10, Timestamp.from(expiresAt));
                    statement.executeUpdate();
                }

                String insertAudit = """
                        INSERT INTO vault_audit_outbox
                            (event_id, tenant_id, vault_id, secret_id, secret_version,
                             actor, event_type, details, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement statement = connection.prepareStatement(insertAudit)) {
                    statement.setObject(1, UUID.randomUUID());
                    statement.setObject(2, tenantId);
                    statement.setObject(3, vaultId.value());
                    statement.setObject(4, secretId.value());
                    statement.setInt(5, newVersion);
                    statement.setString(6, auditEntry.actor());
                    statement.setString(7, auditEntry.eventType());
                    statement.setString(8, auditEntry.details());
                    statement.setTimestamp(9, Timestamp.from(auditEntry.occurredAt()));
                    statement.executeUpdate();
                }
                connection.commit();
                return new EncryptedSecret(tenantId, vaultId, secretId, new SecretVersion(newVersion),
                        algorithm, formatVersion, keyId, ciphertext, createdAt, expiresAt);
            } catch (RuntimeException | SQLException failure) {
                rollback(connection, failure);
                if (failure instanceof RuntimeException runtime) throw runtime;
                SQLException sql = (SQLException) failure;
                if ("23505".equals(sql.getSQLState()))
                    throw new SecretVersionConflictException(expectedPreviousVersion, -1, sql);
                throw new VaultPersistenceException("Unable to append encrypted secret version", sql);
            }
        } catch (SQLException failure) {
            throw new VaultPersistenceException("Unable to open vault transaction", failure);
        } finally {
            java.util.Arrays.fill(ciphertext, (byte) 0);
        }
    }


    private boolean isRevoked(Connection connection, UUID tenantId,
                               VaultId vaultId, SecretId secretId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM vault_secret_lifecycle
                WHERE tenant_id = ? AND vault_id = ? AND secret_id = ? AND status = 'REVOKED'
                """)) {
            bindScope(statement, tenantId, vaultId, secretId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private int lockAndReadLatestVersion(Connection connection, UUID tenantId,
                                         VaultId vaultId, SecretId secretId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT secret_version FROM vault_secret_version
                WHERE tenant_id = ? AND vault_id = ? AND secret_id = ?
                ORDER BY secret_version DESC LIMIT 1 FOR UPDATE
                """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, vaultId.value());
            statement.setObject(3, secretId.value());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }


    @Override
    public Uni<Void> appendAuditEvent(UUID tenantId, VaultId vaultId, SecretId secretId,
                                      int version, VaultAuditEntry auditEntry) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(vaultId, "vaultId");
        Objects.requireNonNull(secretId, "secretId");
        Objects.requireNonNull(auditEntry, "auditEntry");
        if (version < 1) return Uni.createFrom().failure(new IllegalArgumentException("version must be positive"));
        return Uni.createFrom().item(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO vault_audit_outbox
                        (event_id, tenant_id, vault_id, secret_id, secret_version,
                         actor, event_type, details, occurred_at)
                    SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?
                    WHERE EXISTS (
                        SELECT 1 FROM vault_secret_version
                        WHERE tenant_id = ? AND vault_id = ? AND secret_id = ? AND secret_version = ?
                    )
                    """)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, tenantId);
                statement.setObject(3, vaultId.value());
                statement.setObject(4, secretId.value());
                statement.setInt(5, version);
                statement.setString(6, auditEntry.actor());
                statement.setString(7, auditEntry.eventType());
                statement.setString(8, auditEntry.details());
                statement.setTimestamp(9, Timestamp.from(auditEntry.occurredAt()));
                statement.setObject(10, tenantId);
                statement.setObject(11, vaultId.value());
                statement.setObject(12, secretId.value());
                statement.setInt(13, version);
                if (statement.executeUpdate() != 1)
                    throw new IllegalStateException("secret version does not exist");
                return (Void) null;
            } catch (SQLException failure) {
                throw new VaultPersistenceException("Unable to append secret audit event", failure);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Optional<EncryptedSecret>> findVersion(UUID tenantId, VaultId vaultId,
                                                       SecretId secretId, int version) {
        if (version < 1) return Uni.createFrom().failure(new IllegalArgumentException("version must be positive"));
        return query(tenantId, vaultId, secretId, version);
    }

    @Override
    public Uni<Optional<EncryptedSecret>> findLatest(UUID tenantId, VaultId vaultId, SecretId secretId) {
        return Uni.createFrom().item(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                    SELECT tenant_id, vault_id, secret_id, secret_version, encryption_algorithm,
                           format_version, key_id, ciphertext, created_at, expires_at
                    FROM vault_secret_version
                    WHERE tenant_id = ? AND vault_id = ? AND secret_id = ?
                    ORDER BY secret_version DESC LIMIT 1
                    """)) {
                bindScope(statement, tenantId, vaultId, secretId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(map(result)) : Optional.empty();
                }
            } catch (SQLException failure) {
                throw new VaultPersistenceException("Unable to read latest secret version", failure);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Uni<Optional<EncryptedSecret>> query(UUID tenantId, VaultId vaultId, SecretId secretId, int version) {
        return Uni.createFrom().item(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                    SELECT tenant_id, vault_id, secret_id, secret_version, encryption_algorithm,
                           format_version, key_id, ciphertext, created_at, expires_at
                    FROM vault_secret_version
                    WHERE tenant_id = ? AND vault_id = ? AND secret_id = ? AND secret_version = ?
                    """)) {
                bindScope(statement, tenantId, vaultId, secretId);
                statement.setInt(4, version);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(map(result)) : Optional.empty();
                }
            } catch (SQLException failure) {
                throw new VaultPersistenceException("Unable to read secret version", failure);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private static void bindScope(PreparedStatement statement, UUID tenantId,
                                  VaultId vaultId, SecretId secretId) throws SQLException {
        statement.setObject(1, tenantId);
        statement.setObject(2, vaultId.value());
        statement.setObject(3, secretId.value());
    }

    private static EncryptedSecret map(ResultSet result) throws SQLException {
        Timestamp expires = result.getTimestamp("expires_at");
        return new EncryptedSecret(result.getObject("tenant_id", UUID.class),
                VaultId.of(result.getObject("vault_id", UUID.class)),
                SecretId.of(result.getObject("secret_id", UUID.class)),
                new SecretVersion(result.getInt("secret_version")),
                result.getString("encryption_algorithm"), result.getInt("format_version"),
                result.getString("key_id"), result.getBytes("ciphertext"),
                result.getTimestamp("created_at").toInstant(), expires == null ? null : expires.toInstant());
    }

    private static void rollback(Connection connection, Throwable original) {
        try { connection.rollback(); } catch (SQLException rollbackFailure) { original.addSuppressed(rollbackFailure); }
    }
}
