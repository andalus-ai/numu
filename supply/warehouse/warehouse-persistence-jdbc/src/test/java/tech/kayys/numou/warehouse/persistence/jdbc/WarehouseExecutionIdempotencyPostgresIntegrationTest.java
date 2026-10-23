package tech.kayys.numou.warehouse.persistence.jdbc;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.warehouse.application.WarehouseExecutionAttempt;
import tech.kayys.numou.warehouse.application.WarehouseInventoryRecoveryPayload;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in PostgreSQL integration test for tenant-scoped idempotency-key collision handling. */
class WarehouseExecutionIdempotencyPostgresIntegrationTest {
    @Test
    void reusedCommandKeyWithDifferentFingerprintOrPayloadIsRejected() throws Exception {
        String url = System.getenv("NUMOU_TEST_POSTGRES_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "Set NUMOU_TEST_POSTGRES_URL to enable PostgreSQL integration tests");
        String user = System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_USER", "");
        String password = System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_PASSWORD", "");
        DataSource dataSource = new DriverManagerDataSource(url, user, password);
        JdbcWarehouseExecutionAttemptRepository repository = new JdbcWarehouseExecutionAttemptRepository(dataSource);

        String tenant = "it-idempotency-" + UUID.randomUUID();
        String command = "receive-" + UUID.randomUUID();
        WarehouseInventoryRecoveryPayload originalPayload = new WarehouseInventoryRecoveryPayload(
                "receive", "item-original", "warehouse-1", null, "2.0000", "3.5000", "USD", "receipt-1");
        WarehouseExecutionAttempt original = WarehouseExecutionAttempt.prepared(
                tenant, command, "fingerprint-original", "work-1", "line-1", "receive", originalPayload);
        try {
            WarehouseExecutionAttempt inserted = repository.createIfAbsent(original);
            assertEquals(original.fingerprint(), inserted.fingerprint());

            WarehouseExecutionAttempt sameKeyDifferentFingerprint = WarehouseExecutionAttempt.prepared(
                    tenant, command, "fingerprint-different", "work-1", "line-1", "receive", originalPayload);
            assertThrows(JdbcWarehouseExecutionAttemptRepository.IdempotencyConflictException.class,
                    () -> repository.createIfAbsent(sameKeyDifferentFingerprint),
                    "a duplicate key must not silently accept a different fingerprint");

            WarehouseInventoryRecoveryPayload changedPayload = new WarehouseInventoryRecoveryPayload(
                    "receive", "item-substituted", "warehouse-1", null, "2.0000", "3.5000", "USD", "receipt-1");
            WarehouseExecutionAttempt sameFingerprintDifferentPayload = WarehouseExecutionAttempt.prepared(
                    tenant, command, "fingerprint-original", "work-1", "line-1", "receive", changedPayload);
            assertThrows(JdbcWarehouseExecutionAttemptRepository.IdempotencyConflictException.class,
                    () -> repository.createIfAbsent(sameFingerprintDifferentPayload),
                    "a caller must not reuse a fingerprint while substituting the persisted business payload");

            WarehouseExecutionAttempt replay = repository.createIfAbsent(original);
            assertEquals(inserted.version(), replay.version());
            assertEquals(inserted.recoveryPayload(), replay.recoveryPayload());
        } finally {
            try (Connection connection = dataSource.getConnection();
                 var statement = connection.prepareStatement(
                         "DELETE FROM warehouse_execution_attempt WHERE tenant_id = ? AND command_key = ?")) {
                statement.setString(1, tenant);
                statement.setString(2, command);
                statement.executeUpdate();
            }
        }
    }

    private record DriverManagerDataSource(String url, String user, String password) implements DataSource {
        @Override public Connection getConnection() throws SQLException {
            return user == null || user.isBlank()
                    ? DriverManager.getConnection(url)
                    : DriverManager.getConnection(url, user, password);
        }
        @Override public Connection getConnection(String username, String pwd) throws SQLException {
            return DriverManager.getConnection(url, username, pwd);
        }
        @Override public PrintWriter getLogWriter() { return DriverManager.getLogWriter(); }
        @Override public void setLogWriter(PrintWriter out) throws SQLException { DriverManager.setLogWriter(out); }
        @Override public void setLoginTimeout(int seconds) throws SQLException { DriverManager.setLoginTimeout(seconds); }
        @Override public int getLoginTimeout() throws SQLException { return DriverManager.getLoginTimeout(); }
        @Override public Logger getParentLogger() { return Logger.getLogger("java.sql"); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) return iface.cast(this);
            throw new SQLException("Not a wrapper for " + iface.getName());
        }
        @Override public boolean isWrapperFor(Class<?> iface) { return iface.isInstance(this); }
    }
}
