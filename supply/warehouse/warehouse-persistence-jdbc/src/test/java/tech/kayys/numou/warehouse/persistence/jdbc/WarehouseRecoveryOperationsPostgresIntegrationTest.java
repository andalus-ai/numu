package tech.kayys.numou.warehouse.persistence.jdbc;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.warehouse.application.WarehouseExecutionAttempt;
import tech.kayys.numou.warehouse.application.WarehouseExecutionClaim;
import tech.kayys.numou.warehouse.application.WarehouseExecutionAttemptStatus;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in PostgreSQL integration coverage for tenant-scoped recovery operations.
 *
 * <p>Set NUMOU_TEST_POSTGRES_URL to a disposable PostgreSQL database with warehouse
 * migrations V1-V5 applied. Optional credentials are NUMOU_TEST_POSTGRES_USER and
 * NUMOU_TEST_POSTGRES_PASSWORD.</p>
 */
class WarehouseRecoveryOperationsPostgresIntegrationTest {
    @Test
    void deadLettersAreTenantScopedAndManualReplayIsVersionFenced() throws Exception {
        String url = System.getenv("NUMOU_TEST_POSTGRES_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "Set NUMOU_TEST_POSTGRES_URL to enable PostgreSQL integration tests");
        String user = System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_USER", "");
        String password = System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_PASSWORD", "");
        DataSource dataSource = new DriverManagerDataSource(url, user, password);
        JdbcWarehouseExecutionAttemptRepository repository = new JdbcWarehouseExecutionAttemptRepository(dataSource);

        String tenantA = "it-ops-a-" + UUID.randomUUID();
        String tenantB = "it-ops-b-" + UUID.randomUUID();
        String commandA = "dead-letter-" + UUID.randomUUID();
        String commandB = "dead-letter-" + UUID.randomUUID();
        try {
            WarehouseExecutionAttempt deadLetterA = createDeadLetter(repository, tenantA, commandA);
            WarehouseExecutionAttempt deadLetterB = createDeadLetter(repository, tenantB, commandB);

            assertEquals(List.of(commandA), repository.findDeadLetters(tenantA, 100).stream()
                    .map(WarehouseExecutionAttempt::commandKey).filter(commandA::equals).toList());
            assertTrue(repository.findDeadLetters(tenantA, 100).stream()
                    .noneMatch(attempt -> attempt.tenantId().equals(tenantB) || attempt.commandKey().equals(commandB)),
                    "tenant A must not inspect tenant B's dead-letter records");

            assertEquals(1, repository.summarizeRecovery(tenantA).deadLetterCount());
            assertEquals(1, repository.summarizeRecovery(tenantB).deadLetterCount());

            Instant eligibleAt = Instant.now().plusSeconds(120);
            WarehouseExecutionAttempt replayed = repository.replayDeadLetter(
                    tenantA, commandA, deadLetterA.version(), "operator reviewed incident", eligibleAt);
            assertEquals(WarehouseExecutionAttemptStatus.PREPARED, replayed.status());
            assertEquals(deadLetterA.attemptCount() + 1, replayed.attemptCount());
            assertTrue(replayed.lastError().contains("operator reviewed incident"));
            assertEquals(0, repository.summarizeRecovery(tenantA).deadLetterCount(),
                    "replayed record must no longer count as a dead letter");
            assertEquals(1, repository.summarizeRecovery(tenantB).deadLetterCount(),
                    "replaying tenant A must not alter tenant B's summary");

            assertThrows(JdbcWarehouseExecutionAttemptRepository.OptimisticLockException.class,
                    () -> repository.replayDeadLetter(
                            tenantA, commandA, deadLetterA.version(), "stale replay must fail", eligibleAt),
                    "replaying with the pre-replay version must be rejected");
            assertEquals(WarehouseExecutionAttemptStatus.DEAD_LETTER,
                    repository.find(tenantB, commandB).orElseThrow().status(),
                    "the other tenant's record must remain untouched");
        } finally {
            deleteTenant(dataSource, tenantA);
            deleteTenant(dataSource, tenantB);
        }
    }

    private static WarehouseExecutionAttempt createDeadLetter(
            JdbcWarehouseExecutionAttemptRepository repository, String tenant, String command) {
        WarehouseExecutionAttempt prepared = repository.createIfAbsent(WarehouseExecutionAttempt.prepared(
                tenant, command, "fingerprint-" + UUID.randomUUID(),
                "work-" + UUID.randomUUID(), "line-" + UUID.randomUUID(), "receive"));
        WarehouseExecutionAttempt reconciliation = prepared.needsReconciliation("integration failure");
        repository.transition(tenant, command, prepared.version(), reconciliation);

        List<WarehouseExecutionClaim> claims =
                repository.claimRecoverable("integration-ops-" + UUID.randomUUID(), 500, Duration.ofSeconds(60));
        WarehouseExecutionClaim claim = claims.stream()
                .filter(c -> c.attempt().tenantId().equals(tenant) && c.attempt().commandKey().equals(command))
                .findFirst().orElseThrow(() -> new AssertionError("Expected recovery claim for " + command));
        return repository.deadLetter(claim, "terminal integration failure");
    }

    private static void deleteTenant(DataSource dataSource, String tenant) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "DELETE FROM warehouse_execution_attempt WHERE tenant_id = ?")) {
            statement.setString(1, tenant);
            statement.executeUpdate();
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
