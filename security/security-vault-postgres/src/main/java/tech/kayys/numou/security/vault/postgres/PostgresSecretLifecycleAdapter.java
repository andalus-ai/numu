package tech.kayys.numou.security.vault.postgres;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import tech.kayys.numou.security.vault.domain.SecretId;
import tech.kayys.numou.security.vault.domain.SecretStatus;
import tech.kayys.numou.security.vault.domain.VaultId;
import tech.kayys.numou.security.vault.spi.SecretLifecyclePort;
import tech.kayys.numou.security.vault.spi.VaultAuditEntry;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL lifecycle adapter; revocation and its outbox fact share one transaction. */
public final class PostgresSecretLifecycleAdapter implements SecretLifecyclePort {
    private final DataSource dataSource;

    public PostgresSecretLifecycleAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    @Override
    public Uni<Optional<SecretStatus>> status(UUID tenantId, VaultId vaultId, SecretId secretId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(vaultId, "vaultId");
        Objects.requireNonNull(secretId, "secretId");
        return Uni.createFrom().item(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                    SELECT status FROM vault_secret_lifecycle
                    WHERE tenant_id = ? AND vault_id = ? AND secret_id = ?
                    """)) {
                bind(statement, tenantId, vaultId, secretId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next()
                            ? Optional.of(SecretStatus.valueOf(result.getString(1)))
                            : Optional.empty();
                }
            } catch (SQLException failure) {
                throw new VaultPersistenceException("Unable to read secret lifecycle", failure);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Void> revoke(UUID tenantId, VaultId vaultId, SecretId secretId, VaultAuditEntry auditEntry) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(vaultId, "vaultId");
        Objects.requireNonNull(secretId, "secretId");
        Objects.requireNonNull(auditEntry, "auditEntry");
        return Uni.createFrom().item(() -> {
            revokeBlocking(tenantId, vaultId, secretId, auditEntry);
            return (Void) null;
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private void revokeBlocking(UUID tenantId, VaultId vaultId, SecretId secretId,
                                VaultAuditEntry auditEntry) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int latestVersion;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT secret_version FROM vault_secret_version
                        WHERE tenant_id = ? AND vault_id = ? AND secret_id = ?
                        ORDER BY secret_version DESC LIMIT 1 FOR UPDATE
                        """)) {
                    bind(statement, tenantId, vaultId, secretId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new IllegalStateException("secret does not exist");
                        latestVersion = result.getInt(1);
                    }
                }

                boolean alreadyRevoked;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT 1 FROM vault_secret_lifecycle
                        WHERE tenant_id = ? AND vault_id = ? AND secret_id = ?
                        FOR UPDATE
                        """)) {
                    bind(statement, tenantId, vaultId, secretId);
                    try (ResultSet result = statement.executeQuery()) {
                        alreadyRevoked = result.next();
                    }
                }
                if (alreadyRevoked) {
                    connection.commit();
                    return;
                }

                Instant now = Instant.now();
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO vault_secret_lifecycle
                            (tenant_id, vault_id, secret_id, status, updated_at)
                        VALUES (?, ?, ?, 'REVOKED', ?)
                        """)) {
                    bind(statement, tenantId, vaultId, secretId);
                    statement.setTimestamp(4, Timestamp.from(now));
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO vault_audit_outbox
                            (event_id, tenant_id, vault_id, secret_id, secret_version,
                             actor, event_type, details, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    statement.setObject(1, UUID.randomUUID());
                    statement.setObject(2, tenantId);
                    statement.setObject(3, vaultId.value());
                    statement.setObject(4, secretId.value());
                    statement.setInt(5, latestVersion);
                    statement.setString(6, auditEntry.actor());
                    statement.setString(7, auditEntry.eventType());
                    statement.setString(8, auditEntry.details());
                    statement.setTimestamp(9, Timestamp.from(auditEntry.occurredAt()));
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (RuntimeException | SQLException failure) {
                try { connection.rollback(); } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new VaultPersistenceException("Unable to revoke secret", failure);
            }
        } catch (SQLException failure) {
            throw new VaultPersistenceException("Unable to open vault lifecycle transaction", failure);
        }
    }

    private static void bind(PreparedStatement statement, UUID tenantId,
                             VaultId vaultId, SecretId secretId) throws SQLException {
        statement.setObject(1, tenantId);
        statement.setObject(2, vaultId.value());
        statement.setObject(3, secretId.value());
    }
}
