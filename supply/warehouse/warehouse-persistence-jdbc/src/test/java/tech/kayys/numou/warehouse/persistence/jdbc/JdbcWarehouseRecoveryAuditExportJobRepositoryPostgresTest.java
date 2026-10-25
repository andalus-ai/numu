package tech.kayys.numou.warehouse.persistence.jdbc;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import tech.kayys.numou.warehouse.application.WarehouseRecoveryAuditExportJob;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class JdbcWarehouseRecoveryAuditExportJobRepositoryPostgresIT {
    private static final String URL = System.getenv("NUMOU_TEST_POSTGRES_URL");
    private static final String USER = System.getenv("NUMOU_TEST_POSTGRES_USER");
    private static final String PASSWORD = System.getenv("NUMOU_TEST_POSTGRES_PASSWORD");
    private static String schema;
    private static SchemaDataSource dataSource;
    private static JdbcWarehouseRecoveryAuditExportJobRepository repository;

    @BeforeAll
    static void createIsolatedSchema() throws Exception {
        Assumptions.assumeTrue(URL != null && !URL.isBlank(),
                "Set NUMOU_TEST_POSTGRES_URL to enable PostgreSQL integration tests");
        schema = "numou_recovery_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connect(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA \"" + schema + "\"");
        }
        dataSource = new SchemaDataSource(URL, USER, PASSWORD, schema);
        repository = new JdbcWarehouseRecoveryAuditExportJobRepository(dataSource);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE warehouse_recovery_audit_export_job (
                    export_id VARCHAR(120) PRIMARY KEY,
                    tenant_id VARCHAR(120) NOT NULL,
                    requested_by VARCHAR(200) NOT NULL,
                    status VARCHAR(24) NOT NULL CHECK (status IN ('REQUESTED','RUNNING','VERIFYING','COMPLETED','FAILED')),
                    continuation_cursor TEXT,
                    exported_event_count BIGINT NOT NULL DEFAULT 0 CHECK (exported_event_count >= 0),
                    exported_byte_count BIGINT NOT NULL DEFAULT 0 CHECK (exported_byte_count >= 0),
                    artifact_location VARCHAR(2000),
                    failure_code VARCHAR(120),
                    created_at TIMESTAMPTZ NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL,
                    lease_until TIMESTAMPTZ,
                    lease_owner VARCHAR(200),
                    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
                    CHECK ((status = 'RUNNING' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
                        OR (status <> 'RUNNING' AND lease_owner IS NULL AND lease_until IS NULL)),
                    CHECK ((status = 'COMPLETED' AND artifact_location IS NOT NULL) OR status <> 'COMPLETED')
                )
                """);
            statement.execute("CREATE INDEX claim_idx ON warehouse_recovery_audit_export_job (created_at, export_id)");
        }
    }

    @AfterAll
    static void dropIsolatedSchema() throws Exception {
        if (schema == null || URL == null || URL.isBlank()) return;
        try (Connection connection = connect(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
        }
    }

    @BeforeEach
    void clearJobs() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM warehouse_recovery_audit_export_job");
        }
    }

    @Test
    void concurrentWorkersCannotClaimTheSamePendingJob() throws Exception {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        repository.insert(job("pending-" + UUID.randomUUID(), WarehouseRecoveryAuditExportJob.Status.REQUESTED,
                now, null, null, 0));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> claimAfterBarrier("worker-a", now, ready, start));
            Future<?> second = workers.submit(() -> claimAfterBarrier("worker-b", now, ready, start));
            ready.await();
            start.countDown();
            Object a = first.get();
            Object b = second.get();
            int claimed = (a == null ? 0 : 1) + (b == null ? 0 : 1);
            assertEquals(1, claimed, "exactly one worker must claim the pending job");
            var claimedJob = a != null ? (WarehouseRecoveryAuditExportJob) a : (WarehouseRecoveryAuditExportJob) b;
            assertEquals(WarehouseRecoveryAuditExportJob.Status.RUNNING, claimedJob.status());
            assertTrue(claimedJob.leaseOwner().equals("worker-a") || claimedJob.leaseOwner().equals("worker-b"));
            assertEquals(1, claimedJob.version());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void expiredRunningLeaseCanBeReclaimedWithVersionIncrement() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant now = created.plusSeconds(600);
        repository.insert(job("expired-" + UUID.randomUUID(), WarehouseRecoveryAuditExportJob.Status.RUNNING,
                created, "dead-worker", created.plusSeconds(60), 7));

        var claimed = repository.claimNext("replacement-worker", now, now.plusSeconds(120)).orElseThrow();

        assertEquals(WarehouseRecoveryAuditExportJob.Status.RUNNING, claimed.status());
        assertEquals("replacement-worker", claimed.leaseOwner());
        assertEquals(now.plusSeconds(120), claimed.leaseUntil());
        assertEquals(8, claimed.version());
    }

    @Test
    void staleWorkerCannotOverwriteCheckpointAfterLeaseIsReclaimed() throws Exception {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        String exportId = "fenced-" + UUID.randomUUID();
        repository.insert(job(exportId, WarehouseRecoveryAuditExportJob.Status.REQUESTED,
                created, null, null, 0));

        var firstClaim = repository.claimNext("worker-stale", created, created.plusSeconds(120)).orElseThrow();
        Instant reclaimTime = created.plusSeconds(300);
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "UPDATE warehouse_recovery_audit_export_job SET lease_until=?, updated_at=? WHERE tenant_id=? AND export_id=?")) {
            statement.setTimestamp(1, java.sql.Timestamp.from(reclaimTime.minusSeconds(1)));
            statement.setTimestamp(2, java.sql.Timestamp.from(reclaimTime));
            statement.setString(3, firstClaim.tenantId());
            statement.setString(4, exportId);
            assertEquals(1, statement.executeUpdate());
        }

        var replacement = repository.claimNext("worker-replacement", reclaimTime,
                reclaimTime.plusSeconds(120)).orElseThrow();
        assertEquals(firstClaim.version() + 1, replacement.version());
        assertEquals("worker-replacement", replacement.leaseOwner());

        // Simulate the old process waking up and attempting to persist work based on its
        // pre-reclaim version. The version predicate is the fencing token for every update.
        var staleCheckpoint = firstClaim.checkpoint("worker-stale", "cursor-stale",
                9, 4096, created.plusSeconds(30), created.plusSeconds(150));
        assertFalse(repository.update(staleCheckpoint, firstClaim.version()),
                "stale worker must lose the optimistic update after the replacement claim");

        var persisted = repository.find(firstClaim.tenantId(), exportId).orElseThrow();
        assertEquals("worker-replacement", persisted.leaseOwner());
        assertEquals(replacement.version(), persisted.version());
        assertNull(persisted.continuationCursor());
        assertEquals(0, persisted.exportedEventCount());
        assertEquals(0, persisted.exportedByteCount());
    }

    private static Object claimAfterBarrier(String worker, Instant now,
            CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            return repository.claimNext(worker, now, now.plusSeconds(120)).orElse(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static WarehouseRecoveryAuditExportJob job(String id, WarehouseRecoveryAuditExportJob.Status status,
            Instant created, String owner, Instant leaseUntil, long version) {
        return new WarehouseRecoveryAuditExportJob(id, "tenant-it", "integration-test", status,
                null, 0, 0, null, null, created, created, leaseUntil, owner, version);
    }

    private static Connection connect(String url, String user, String password) throws SQLException {
        if (user == null || user.isBlank()) return DriverManager.getConnection(url);
        return DriverManager.getConnection(url, user, password == null ? "" : password);
    }

    private static final class SchemaDataSource implements DataSource {
        private final String url;
        private final String user;
        private final String password;
        private final String schema;

        private SchemaDataSource(String url, String user, String password, String schema) {
            this.url = url;
            this.user = user;
            this.password = password;
            this.schema = schema;
        }

        @Override public Connection getConnection() throws SQLException {
            Connection connection = connect(url, user, password);
            try {
                connection.setSchema(schema);
                return connection;
            } catch (SQLException e) {
                connection.close();
                throw e;
            }
        }
        @Override public Connection getConnection(String username, String password) throws SQLException {
            Connection connection = DriverManager.getConnection(url, username, password);
            try {
                connection.setSchema(schema);
                return connection;
            } catch (SQLException e) {
                connection.close();
                throw e;
            }
        }
        @Override public PrintWriter getLogWriter() { return DriverManager.getLogWriter(); }
        @Override public void setLogWriter(PrintWriter out) { DriverManager.setLogWriter(out); }
        @Override public void setLoginTimeout(int seconds) { DriverManager.setLoginTimeout(seconds); }
        @Override public int getLoginTimeout() { return DriverManager.getLoginTimeout(); }
        @Override public Logger getParentLogger() { return Logger.getLogger("org.postgresql"); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) return iface.cast(this);
            throw new SQLException("Not a wrapper for " + iface.getName());
        }
        @Override public boolean isWrapperFor(Class<?> iface) { return iface.isInstance(this); }
    }
}
