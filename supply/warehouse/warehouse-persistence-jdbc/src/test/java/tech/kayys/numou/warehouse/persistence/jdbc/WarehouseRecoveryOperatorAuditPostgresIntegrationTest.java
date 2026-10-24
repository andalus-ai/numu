package tech.kayys.numou.warehouse.persistence.jdbc;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.warehouse.application.WarehouseExecutionAttempt;
import tech.kayys.numou.warehouse.application.WarehouseExecutionClaim;
import tech.kayys.numou.warehouse.application.WarehouseRecoveryOperatorAuditEvent;

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

/** Opt-in PostgreSQL test for atomic, tenant-scoped operator replay audit history. */
class WarehouseRecoveryOperatorAuditPostgresIntegrationTest {
    @Test
    void replayAndAuditAreAtomicAndAuditHistoryIsTenantScoped() throws Exception {
        String url = System.getenv("NUMOU_TEST_POSTGRES_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "Set NUMOU_TEST_POSTGRES_URL to enable PostgreSQL integration tests");
        String user = System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_USER", "");
        String password = System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_PASSWORD", "");
        DataSource dataSource = new DriverManagerDataSource(url, user, password);
        JdbcWarehouseExecutionAttemptRepository repository = new JdbcWarehouseExecutionAttemptRepository(dataSource);

        String tenant = "it-audit-" + UUID.randomUUID();
        String otherTenant = "it-audit-other-" + UUID.randomUUID();
        String command = "audit-replay-" + UUID.randomUUID();
        String otherCommand = "audit-replay-" + UUID.randomUUID();
        String correlation = "corr-" + UUID.randomUUID();
        try {
            WarehouseExecutionAttempt deadLetter = createDeadLetter(repository, tenant, command);
            createDeadLetter(repository, otherTenant, otherCommand);

            WarehouseExecutionAttempt replayed = repository.replayDeadLetter(
                    tenant, command, deadLetter.version(), "operator-42",
                    "approved after warehouse reconciliation", correlation, Instant.now().plusSeconds(90));

            assertEquals(deadLetter.version() + 1, replayed.version());
            List<WarehouseRecoveryOperatorAuditEvent> events = repository.findOperatorAuditEvents(tenant, 20);
            assertEquals(1, events.size());
            WarehouseRecoveryOperatorAuditEvent event = events.get(0);
            assertEquals(tenant, event.tenantId());
            assertEquals(command, event.commandKey());
            assertEquals("DEAD_LETTER_REPLAY", event.action());
            assertEquals("operator-42", event.actorId());
            assertEquals("approved after warehouse reconciliation", event.reason());
            assertEquals(deadLetter.version(), event.priorVersion());
            assertEquals(replayed.version(), event.resultingVersion());
            assertEquals(correlation, event.correlationId());

            assertTrue(repository.findOperatorAuditEvents(otherTenant, 20).isEmpty(),
                    "one tenant must not inspect another tenant's audit history");

            assertThrows(JdbcWarehouseExecutionAttemptRepository.OptimisticLockException.class,
                    () -> repository.replayDeadLetter(tenant, command, deadLetter.version(),
                            "operator-42", "stale attempt", correlation, Instant.now().plusSeconds(90)));
            assertEquals(1, repository.findOperatorAuditEvents(tenant, 20).size(),
                    "a rejected stale replay must not append an audit event");
        } finally {
            deleteTenant(dataSource, tenant);
            deleteTenant(dataSource, otherTenant);
        }
    }

    private static WarehouseExecutionAttempt createDeadLetter(
            JdbcWarehouseExecutionAttemptRepository repository, String tenant, String command) {
        WarehouseExecutionAttempt prepared = repository.createIfAbsent(WarehouseExecutionAttempt.prepared(
                tenant, command, "fingerprint-" + UUID.randomUUID(),
                "work-" + UUID.randomUUID(), "line-" + UUID.randomUUID(), "receive"));
        WarehouseExecutionAttempt reconciliation = prepared.needsReconciliation("integration failure");
        repository.transition(tenant, command, prepared.version(), reconciliation);
        List<WarehouseExecutionClaim> claims = repository.claimRecoverable(
                "audit-worker-" + UUID.randomUUID(), 500, Duration.ofSeconds(60));
        WarehouseExecutionClaim claim = claims.stream()
                .filter(c -> c.attempt().tenantId().equals(tenant) && c.attempt().commandKey().equals(command))
                .findFirst().orElseThrow(() -> new AssertionError("Expected claim for " + command));
        return repository.deadLetter(claim, "terminal integration failure");
    }

    private static void deleteTenant(DataSource dataSource, String tenant) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (var audit = connection.prepareStatement(
                    "DELETE FROM warehouse_recovery_operator_audit WHERE tenant_id=?")) {
                audit.setString(1, tenant);
                audit.executeUpdate();
            }
            try (var attempts = connection.prepareStatement(
                    "DELETE FROM warehouse_execution_attempt WHERE tenant_id=?")) {
                attempts.setString(1, tenant);
                attempts.executeUpdate();
            }
        }
    }

    private record DriverManagerDataSource(String url, String user, String password) implements DataSource {
        @Override public Connection getConnection() throws SQLException {
            return user == null || user.isBlank() ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user, password);
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
