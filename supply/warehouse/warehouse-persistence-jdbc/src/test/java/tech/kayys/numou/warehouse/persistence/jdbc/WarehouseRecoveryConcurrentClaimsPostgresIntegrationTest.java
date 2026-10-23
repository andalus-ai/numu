package tech.kayys.numou.warehouse.persistence.jdbc;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.warehouse.application.WarehouseExecutionAttempt;
import tech.kayys.numou.warehouse.application.WarehouseExecutionClaim;
import tech.kayys.numou.warehouse.application.WarehouseInventoryRecoveryPayload;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in two-worker PostgreSQL contention test. Uses separate JDBC connections through
 * the repository and requires migrations V1-V4 in a disposable PostgreSQL database.
 */
class WarehouseRecoveryConcurrentClaimsPostgresIntegrationTest {
    @Test
    void concurrentWorkersNeverReceiveTheSameRecoveryAttempt() throws Exception {
        String url = System.getenv("NUMOU_TEST_POSTGRES_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "Set NUMOU_TEST_POSTGRES_URL to enable PostgreSQL integration tests");
        String user = System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_USER", "");
        String password = System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_PASSWORD", "");
        DataSource dataSource = new DriverManagerDataSource(url, user, password);
        JdbcWarehouseExecutionAttemptRepository repository = new JdbcWarehouseExecutionAttemptRepository(dataSource);

        String tenant = "it-concurrent-" + UUID.randomUUID();
        List<String> commands = new ArrayList<>();
        for (int i = 0; i < 8; i++) commands.add("recover-" + i + "-" + UUID.randomUUID());
        try {
            for (String command : commands) {
                WarehouseInventoryRecoveryPayload payload = new WarehouseInventoryRecoveryPayload(
                        "receive", "item-" + command, "warehouse-it", null,
                        "1.0000", "2.5000", "USD", "concurrent-claim-test");
                repository.createIfAbsent(WarehouseExecutionAttempt.prepared(
                        tenant, command, "fingerprint-" + UUID.randomUUID(),
                        "work-" + command, "line-" + command, "receive", payload));
            }

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                Future<List<WarehouseExecutionClaim>> workerA = workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start barrier timed out");
                    return repository.claimRecoverable("integration-worker-a", 500, Duration.ofSeconds(60));
                });
                Future<List<WarehouseExecutionClaim>> workerB = workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start barrier timed out");
                    return repository.claimRecoverable("integration-worker-b", 500, Duration.ofSeconds(60));
                });

                assertTrue(ready.await(10, TimeUnit.SECONDS), "both workers should be ready");
                start.countDown();
                List<WarehouseExecutionClaim> claimsA = workerA.get(30, TimeUnit.SECONDS);
                List<WarehouseExecutionClaim> claimsB = workerB.get(30, TimeUnit.SECONDS);

                Set<String> targetA = targetCommands(claimsA, tenant, commands);
                Set<String> targetB = targetCommands(claimsB, tenant, commands);
                Set<String> overlap = new HashSet<>(targetA);
                overlap.retainAll(targetB);
                assertTrue(overlap.isEmpty(), "a recovery attempt must never be leased to both workers");
                Set<String> claimed = new HashSet<>(targetA);
                claimed.addAll(targetB);
                assertEquals(new HashSet<>(commands), claimed,
                        "all inserted attempts should be claimed by exactly one worker");
            } finally {
                workers.shutdownNow();
            }
        } finally {
            try (Connection connection = dataSource.getConnection();
                 var statement = connection.prepareStatement(
                         "DELETE FROM warehouse_execution_attempt WHERE tenant_id = ? AND command_key LIKE 'recover-%'")) {
                statement.setString(1, tenant);
                statement.executeUpdate();
            }
        }
    }

    private static Set<String> targetCommands(List<WarehouseExecutionClaim> claims, String tenant, List<String> targets) {
        Set<String> targetSet = new HashSet<>(targets);
        Set<String> found = new HashSet<>();
        for (WarehouseExecutionClaim claim : claims) {
            if (tenant.equals(claim.attempt().tenantId()) && targetSet.contains(claim.attempt().commandKey())) {
                found.add(claim.attempt().commandKey());
            }
        }
        return found;
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
