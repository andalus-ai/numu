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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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


    @Test
    void auditInsertFailureRollsBackTheReplayTransition() throws Exception {
        String url = System.getenv("NUMOU_TEST_POSTGRES_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "Set NUMOU_TEST_POSTGRES_URL to enable PostgreSQL integration tests");
        DataSource dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_USER", ""),
                System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_PASSWORD", ""));
        JdbcWarehouseExecutionAttemptRepository repository = new JdbcWarehouseExecutionAttemptRepository(dataSource);
        String tenant = "it-audit-rollback-" + UUID.randomUUID();
        String command = "audit-rollback-" + UUID.randomUUID();
        String constraint = "ck_audit_reject_" + UUID.randomUUID().toString().replace("-", "");
        WarehouseExecutionAttempt deadLetter = null;
        try {
            deadLetter = createDeadLetter(repository, tenant, command);
            try (Connection connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE warehouse_recovery_operator_audit ADD CONSTRAINT "
                        + constraint + " CHECK (actor_id <> 'operator-fail')");
            }
            WarehouseExecutionAttempt original = deadLetter;
            assertThrows(IllegalStateException.class, () -> repository.replayDeadLetter(
                    tenant, command, original.version(), "operator-fail",
                    "exercise transaction rollback", null, Instant.now().plusSeconds(90)));

            WarehouseExecutionAttempt persisted = repository.find(tenant, command).orElseThrow();
            assertEquals("DEAD_LETTER", persisted.status().name(),
                    "attempt transition must roll back when the audit insert fails");
            assertEquals(original.version(), persisted.version());
            assertTrue(repository.findOperatorAuditEvents(tenant, 20).isEmpty());
        } finally {
            try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE warehouse_recovery_operator_audit DROP CONSTRAINT IF EXISTS " + constraint);
            } finally {
                deleteTenant(dataSource, tenant);
            }
        }
    }

    @Test
    void concurrentReplayAllowsExactlyOneTransitionAndAuditEvent() throws Exception {
        String url = System.getenv("NUMOU_TEST_POSTGRES_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "Set NUMOU_TEST_POSTGRES_URL to enable PostgreSQL integration tests");
        DataSource dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_USER", ""),
                System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_PASSWORD", ""));
        JdbcWarehouseExecutionAttemptRepository repository = new JdbcWarehouseExecutionAttemptRepository(dataSource);
        String tenant = "it-audit-race-" + UUID.randomUUID();
        String command = "audit-race-" + UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            WarehouseExecutionAttempt deadLetter = createDeadLetter(repository, tenant, command);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger successes = new AtomicInteger();
            AtomicInteger staleRejections = new AtomicInteger();
            List<Future<?>> tasks = List.of(
                    executor.submit(() -> raceReplay(repository, tenant, command, deadLetter.version(), "operator-a", ready, start, successes, staleRejections)),
                    executor.submit(() -> raceReplay(repository, tenant, command, deadLetter.version(), "operator-b", ready, start, successes, staleRejections)));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "both replay workers should be ready");
            start.countDown();
            for (Future<?> task : tasks) task.get(10, TimeUnit.SECONDS);

            assertEquals(1, successes.get(), "only one version-fenced replay may succeed");
            assertEquals(1, staleRejections.get(), "the competing replay must be rejected as stale");
            List<WarehouseRecoveryOperatorAuditEvent> events = repository.findOperatorAuditEvents(tenant, 20);
            assertEquals(1, events.size(), "exactly one successful replay must have exactly one audit event");
            assertEquals(deadLetter.version() + 1, events.get(0).resultingVersion());
            assertTrue(List.of("operator-a", "operator-b").contains(events.get(0).actorId()));
        } finally {
            executor.shutdownNow();
            deleteTenant(dataSource, tenant);
        }
    }

    private static void raceReplay(JdbcWarehouseExecutionAttemptRepository repository,
                                   String tenant, String command, long version, String actor,
                                   CountDownLatch ready, CountDownLatch start,
                                   AtomicInteger successes, AtomicInteger staleRejections) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("timed out waiting to start race");
            repository.replayDeadLetter(tenant, command, version, actor,
                    "concurrent replay test", "race-correlation", Instant.now().plusSeconds(90));
            successes.incrementAndGet();
        } catch (JdbcWarehouseExecutionAttemptRepository.OptimisticLockException expected) {
            staleRejections.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("replay race interrupted", e);
        }
    }


    @Test
    void auditPagesAreBoundedAndDoNotRepeatEventsAcrossCursors() throws Exception {
        String url = System.getenv("NUMOU_TEST_POSTGRES_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "Set NUMOU_TEST_POSTGRES_URL to enable PostgreSQL integration tests");
        DataSource dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_USER", ""),
                System.getenv().getOrDefault("NUMOU_TEST_POSTGRES_PASSWORD", ""));
        JdbcWarehouseExecutionAttemptRepository repository = new JdbcWarehouseExecutionAttemptRepository(dataSource);
        String tenant = "it-audit-page-" + UUID.randomUUID();
        try {
            for (int i = 0; i < 3; i++) {
                String command = "audit-page-" + i + "-" + UUID.randomUUID();
                WarehouseExecutionAttempt deadLetter = createDeadLetter(repository, tenant, command);
                repository.replayDeadLetter(tenant, command, deadLetter.version(),
                        "page-operator", "pagination test " + i, "page-correlation-" + i,
                        Instant.now().plusSeconds(120 + i));
            }

            var first = repository.pageOperatorAuditEvents(tenant, 2, null);
            assertEquals(2, first.events().size());
            assertTrue(first.hasMore());
            var second = repository.pageOperatorAuditEvents(tenant, 2, first.nextCursor());
            assertEquals(1, second.events().size());
            assertFalse(second.hasMore());

            List<String> commandKeys = new java.util.ArrayList<>();
            first.events().forEach(event -> commandKeys.add(event.commandKey()));
            second.events().forEach(event -> commandKeys.add(event.commandKey()));
            assertEquals(3, new java.util.HashSet<>(commandKeys).size(),
                    "cursor pagination must not duplicate audit events");
            assertEquals(3, repository.findOperatorAuditEvents(tenant, 10).size());

            assertThrows(IllegalArgumentException.class,
                    () -> repository.pageOperatorAuditEvents(tenant, 2, "not-a-valid-cursor"));
            assertThrows(IllegalArgumentException.class,
                    () -> repository.pageOperatorAuditEvents(" ", 2, null));
        } finally {
            deleteTenant(dataSource, tenant);
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
