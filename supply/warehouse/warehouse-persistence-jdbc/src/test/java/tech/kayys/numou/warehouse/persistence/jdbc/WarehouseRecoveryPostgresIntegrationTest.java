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
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in PostgreSQL integration test. Point NUMOU_TEST_POSTGRES_URL at a disposable
 * PostgreSQL database with warehouse migrations V1-V4 applied. Optional credentials are
 * read from NUMOU_TEST_POSTGRES_USER and NUMOU_TEST_POSTGRES_PASSWORD.
 */
class WarehouseRecoveryPostgresIntegrationTest {
    @Test
    void recoveryPayloadRoundTripsAndLiveClaimIsNotReturnedToAnotherWorker() throws Exception {
        String url = System.getenv("NUMOU_TEST_POSTGRES_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "Set NUMOU_TEST_POSTGRES_URL to enable PostgreSQL integration tests");
        String user = System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_USER", "");
        String password = System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_PASSWORD", "");
        DataSource dataSource = new DriverManagerDataSource(url, user, password);
        JdbcWarehouseExecutionAttemptRepository repository = new JdbcWarehouseExecutionAttemptRepository(dataSource);

        String tenant = "it-" + UUID.randomUUID();
        String command = "receive-" + UUID.randomUUID();
        WarehouseInventoryRecoveryPayload payload = new WarehouseInventoryRecoveryPayload(
                "receive", "item-42", "warehouse-9", null,
                "0.00012500", "19.9900", "USD", "integration-test-reference");
        WarehouseExecutionAttempt proposed = WarehouseExecutionAttempt.prepared(
                tenant, command, "fingerprint-" + UUID.randomUUID(), "work-1", "line-1", "receive", payload);

        try {
            WarehouseExecutionAttempt inserted = repository.createIfAbsent(proposed);
            assertEquals(payload, inserted.recoveryPayload());
            assertEquals("0.00012500", inserted.recoveryPayload().quantity());
            assertEquals("19.9900", inserted.recoveryPayload().unitCost());

            try (Connection connection = dataSource.getConnection();
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM warehouse_execution_attempt " +
                         "WHERE status IN ('PREPARED','INVENTORY_POSTED','NEEDS_RECONCILIATION') " +
                         "AND (claim_until IS NULL OR claim_until < CURRENT_TIMESTAMP)")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1),
                        "Use a disposable PostgreSQL database with no other unclaimed recovery attempts");
            }

            List<tech.kayys.numou.warehouse.application.WarehouseExecutionClaim> first =
                    repository.claimRecoverable("integration-worker-a", 500, Duration.ofSeconds(60));
            assertTrue(first.stream().anyMatch(c -> c.attempt().tenantId().equals(tenant)
                    && c.attempt().commandKey().equals(command)));

            List<tech.kayys.numou.warehouse.application.WarehouseExecutionClaim> second =
                    repository.claimRecoverable("integration-worker-b", 500, Duration.ofSeconds(60));
            assertFalse(second.stream().anyMatch(c -> c.attempt().tenantId().equals(tenant)
                    && c.attempt().commandKey().equals(command)),
                    "a second worker must not claim an attempt with a live lease");
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
            return DriverManager.getConnection(url, user, password);
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
