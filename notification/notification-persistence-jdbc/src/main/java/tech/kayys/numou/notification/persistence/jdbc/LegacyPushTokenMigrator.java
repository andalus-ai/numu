package tech.kayys.numou.notification.persistence.jdbc;

import tech.kayys.numou.notification.application.port.PushTokenProtector;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded, resumable migration of pre-encryption push credentials.
 *
 * <p>Rows are claimed with FOR UPDATE SKIP LOCKED and converted in the same
 * transaction. The audit table contains counts and outcome codes only.</p>
 */
public final class LegacyPushTokenMigrator {
    private final DataSource dataSource;
    private final PushTokenProtector protector;
    private final Clock clock;

    public LegacyPushTokenMigrator(DataSource dataSource, PushTokenProtector protector, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
        this.protector = Objects.requireNonNull(protector, "protector is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public BatchResult migrateBatch(int batchSize) {
        if (batchSize < 1 || batchSize > 1_000)
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");

        UUID batchId = UUID.randomUUID();
        Instant startedAt = clock.instant();
        int scanned = 0;
        int migrated = 0;

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<LegacyToken> rows = lockLegacyRows(connection, batchSize);
                scanned = rows.size();
                for (LegacyToken row : rows) {
                    PushTokenProtector.EncryptedToken encrypted =
                            protector.protect(row.tenantId(), row.recipientId(), row.plaintextToken());
                    migrated += encryptRow(connection, row, encrypted);
                }
                long remaining = countLegacyRows(connection);
                Instant completedAt = clock.instant();
                insertAudit(connection, batchId, startedAt, completedAt, scanned, migrated,
                        remaining, "SUCCEEDED", null);
                connection.commit();
                return new BatchResult(batchId, scanned, migrated, remaining, startedAt, completedAt);
            } catch (Exception failure) {
                rollbackQuietly(connection);
                recordFailure(batchId, startedAt, scanned);
                throw new IllegalStateException("Legacy push-token migration batch failed", failure);
            }
        } catch (SQLException failure) {
            recordFailure(batchId, startedAt, scanned);
            throw new IllegalStateException("Legacy push-token migration batch failed", failure);
        }
    }

    private List<LegacyToken> lockLegacyRows(Connection connection, int batchSize) throws SQLException {
        String sql = """
                SELECT id, tenant_id, recipient_id, provider_token
                FROM notification_push_device
                WHERE provider_token IS NOT NULL
                  AND provider_token_ciphertext IS NULL
                  AND provider_token_key_id IS NULL
                ORDER BY id
                FOR UPDATE SKIP LOCKED
                LIMIT ?
                """;
        List<LegacyToken> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, batchSize);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new LegacyToken(
                            result.getObject("id", UUID.class),
                            result.getObject("tenant_id", UUID.class),
                            result.getObject("recipient_id", UUID.class),
                            result.getString("provider_token")));
                }
            }
        }
        return rows;
    }

    private int encryptRow(Connection connection, LegacyToken row,
                           PushTokenProtector.EncryptedToken encrypted) throws SQLException {
        String sql = """
                UPDATE notification_push_device
                SET provider_token = NULL,
                    provider_token_ciphertext = ?,
                    provider_token_key_id = ?,
                    updated_at = GREATEST(updated_at, ?)
                WHERE id = ?
                  AND tenant_id = ?
                  AND recipient_id = ?
                  AND provider_token IS NOT NULL
                  AND provider_token_ciphertext IS NULL
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, encrypted.ciphertext());
            statement.setString(2, encrypted.keyId());
            statement.setTimestamp(3, Timestamp.from(clock.instant()));
            statement.setObject(4, row.id());
            statement.setObject(5, row.tenantId());
            statement.setObject(6, row.recipientId());
            int changed = statement.executeUpdate();
            if (changed != 1) throw new SQLException("Legacy push-token row changed during migration");
            return changed;
        }
    }

    private long countLegacyRows(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM notification_push_device
                WHERE provider_token IS NOT NULL
                  AND provider_token_ciphertext IS NULL
                  AND provider_token_key_id IS NULL
                """);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private void insertAudit(Connection connection, UUID id, Instant startedAt, Instant completedAt,
                             int scanned, int migrated, long remaining,
                             String outcome, String failureCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO notification_push_token_migration_audit
                    (id, started_at, completed_at, scanned_count, migrated_count,
                     remaining_count, outcome, failure_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, id);
            statement.setTimestamp(2, Timestamp.from(startedAt));
            statement.setTimestamp(3, Timestamp.from(completedAt));
            statement.setInt(4, scanned);
            statement.setInt(5, migrated);
            statement.setLong(6, remaining);
            statement.setString(7, outcome);
            statement.setString(8, failureCode);
            statement.executeUpdate();
        }
    }

    private void recordFailure(UUID id, Instant startedAt, int scanned) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            long remaining = countLegacyRows(connection);
            insertAudit(connection, id, startedAt, clock.instant(), scanned, 0,
                    remaining, "FAILED", "BATCH_FAILED");
            connection.commit();
        } catch (SQLException ignored) {
            // Do not mask the original failure or emit credential-bearing database details.
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try { connection.rollback(); } catch (SQLException ignored) { /* preserve original failure */ }
    }

    private record LegacyToken(UUID id, UUID tenantId, UUID recipientId, String plaintextToken) {}

    public record BatchResult(UUID batchId, int scanned, int migrated, long remaining,
                              Instant startedAt, Instant completedAt) {}
}
