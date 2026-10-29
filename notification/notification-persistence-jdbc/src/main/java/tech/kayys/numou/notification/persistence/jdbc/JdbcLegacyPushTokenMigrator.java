package tech.kayys.numou.notification.persistence.jdbc;

import tech.kayys.numou.notification.application.port.PushTokenProtector;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;

/**
 * Bounded, resumable migration of legacy plaintext push tokens to the encrypted
 * storage columns introduced by migration V4. Invoke only from an explicitly
 * authorized maintenance job after configuring a managed key provider.
 *
 * <p>Each batch is atomic. Rows locked by another migration worker are skipped,
 * so multiple workers can safely process disjoint batches. Plaintext values are
 * never included in results or exception messages.</p>
 */
public final class JdbcLegacyPushTokenMigrator {
    private static final int MAX_BATCH_SIZE = 1_000;

    private final DataSource dataSource;
    private final PushTokenProtector tokenProtector;

    public JdbcLegacyPushTokenMigrator(DataSource dataSource, PushTokenProtector tokenProtector) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
        this.tokenProtector = Objects.requireNonNull(tokenProtector, "tokenProtector is required");
    }

    /**
     * Encrypts up to {@code batchSize} legacy rows in one transaction.
     * Safe to rerun: already encrypted rows are not selected.
     */
    public BatchResult migrateBatch(int batchSize) {
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize must be between 1 and " + MAX_BATCH_SIZE);
        }

        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int migrated = encryptBatch(connection, batchSize);
                long remaining = countLegacyRows(connection);
                connection.commit();
                return new BatchResult(migrated, remaining);
            } catch (Exception failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                if (failure instanceof IllegalArgumentException illegalArgument) throw illegalArgument;
                throw new IllegalStateException("Legacy push-token migration batch failed; batch rolled back");
            } finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // Connection is closed immediately after this method.
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to execute legacy push-token migration batch");
        }
    }

    private int encryptBatch(Connection connection, int batchSize) throws SQLException {
        String selectSql = """
                SELECT id, tenant_id, recipient_id, provider_token
                FROM notification_push_device
                WHERE provider_token IS NOT NULL
                  AND provider_token_ciphertext IS NULL
                  AND provider_token_key_id IS NULL
                ORDER BY created_at, id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """;
        String updateSql = """
                UPDATE notification_push_device
                SET provider_token = NULL,
                    provider_token_ciphertext = ?,
                    provider_token_key_id = ?
                WHERE id = ?
                  AND provider_token IS NOT NULL
                  AND provider_token_ciphertext IS NULL
                  AND provider_token_key_id IS NULL
                """;
        int migrated = 0;
        try (PreparedStatement select = connection.prepareStatement(selectSql)) {
            select.setInt(1, batchSize);
            try (ResultSet rows = select.executeQuery();
                 PreparedStatement update = connection.prepareStatement(updateSql)) {
                while (rows.next()) {
                    var tenantId = rows.getObject("tenant_id", java.util.UUID.class);
                    var recipientId = rows.getObject("recipient_id", java.util.UUID.class);
                    String plaintext = rows.getString("provider_token");
                    PushTokenProtector.EncryptedToken encrypted =
                            tokenProtector.protect(tenantId, recipientId, plaintext);
                    update.setString(1, encrypted.ciphertext());
                    update.setString(2, encrypted.keyId());
                    update.setObject(3, rows.getObject("id", java.util.UUID.class));
                    if (update.executeUpdate() != 1) {
                        throw new IllegalStateException("Legacy push-token row changed during migration");
                    }
                    migrated++;
                }
            }
        }
        return migrated;
    }

    private long countLegacyRows(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM notification_push_device
                WHERE provider_token IS NOT NULL
                   OR provider_token_ciphertext IS NULL
                   OR provider_token_key_id IS NULL
                """);
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) throw new SQLException("Unable to count legacy push-token rows");
            return rows.getLong(1);
        }
    }

    public record BatchResult(int migratedInBatch, long remainingLegacyRows) {
        public BatchResult {
            if (migratedInBatch < 0 || remainingLegacyRows < 0) {
                throw new IllegalArgumentException("migration counts cannot be negative");
            }
        }

        public boolean complete() {
            return remainingLegacyRows == 0;
        }
    }
}
