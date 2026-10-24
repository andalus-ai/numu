package tech.kayys.numou.warehouse.persistence.jdbc;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.numou.warehouse.application.*;
import tech.kayys.numou.warehouse.artifact.filesystem.FilesystemWarehouseRecoveryAuditArtifactStore;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class WarehouseRecoveryAuditWorkerPostgresFilesystemRecoveryIT {
    private static final String URL = System.getenv("NUMOU_TEST_POSTGRES_URL");
    private static final String USER = System.getenv("NUMOU_TEST_POSTGRES_USER");
    private static final String PASSWORD = System.getenv("NUMOU_TEST_POSTGRES_PASSWORD");
    private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");
    private static String schema;
    private static SchemaDataSource dataSource;
    private static JdbcWarehouseRecoveryAuditExportJobRepository jobs;
    private static JdbcWarehouseRecoveryAuditArchiveManifestRepository manifests;
    private static JdbcWarehouseRecoveryAuditExportAccessRepository access;

    @TempDir Path artifactRoot;

    @BeforeAll
    static void createSchema() throws Exception {
        Assumptions.assumeTrue(URL != null && !URL.isBlank(),
                "Set NUMOU_TEST_POSTGRES_URL to enable PostgreSQL recovery integration tests");
        schema = "numou_worker_recovery_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connect(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA \"" + schema + "\"");
        }
        dataSource = new SchemaDataSource(URL, USER, PASSWORD, schema);
        jobs = new JdbcWarehouseRecoveryAuditExportJobRepository(dataSource);
        manifests = new JdbcWarehouseRecoveryAuditArchiveManifestRepository(dataSource);
        access = new JdbcWarehouseRecoveryAuditExportAccessRepository(dataSource);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
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
            statement.execute("""
                CREATE TABLE warehouse_recovery_audit_archive_manifest (
                    export_id VARCHAR(120) PRIMARY KEY,
                    tenant_id VARCHAR(120) NOT NULL,
                    artifact_location VARCHAR(2000) NOT NULL,
                    sha256_hex CHAR(64) NOT NULL CHECK (sha256_hex ~ '^[0-9a-f]{64}$'),
                    byte_size BIGINT NOT NULL CHECK (byte_size >= 0),
                    event_count BIGINT NOT NULL CHECK (event_count >= 0),
                    created_at TIMESTAMPTZ NOT NULL,
                    created_by VARCHAR(200) NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE warehouse_recovery_audit_export_access (
                    event_id VARCHAR(120) PRIMARY KEY,
                    tenant_id VARCHAR(120) NOT NULL,
                    export_id VARCHAR(120) NOT NULL,
                    actor_id VARCHAR(200) NOT NULL,
                    action VARCHAR(24) NOT NULL CHECK (action IN ('REQUEST','CLAIM','RESUME','DOWNLOAD','VERIFY','VERIFY_FAILED')),
                    outcome VARCHAR(24) NOT NULL CHECK (outcome IN ('ALLOWED','DENIED','SUCCESS','FAILURE')),
                    reason_code VARCHAR(120),
                    occurred_at TIMESTAMPTZ NOT NULL
                )
                """);
        }
    }

    @AfterAll
    static void dropSchema() throws Exception {
        if (schema == null || URL == null || URL.isBlank()) return;
        try (Connection connection = connect(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
        }
    }

    @BeforeEach
    void clearDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM warehouse_recovery_audit_export_access");
            statement.executeUpdate("DELETE FROM warehouse_recovery_audit_archive_manifest");
            statement.executeUpdate("DELETE FROM warehouse_recovery_audit_export_job");
        }
    }

    @Test
    void recoversDurableFilesystemChunkAfterRealPostgresCheckpointFailure() throws Exception {
        String exportId = "export-" + UUID.randomUUID();
        WarehouseRecoveryAuditExportJob initial = new WarehouseRecoveryAuditExportJob(
                exportId, "tenant-postgres-it", "operator-it", WarehouseRecoveryAuditExportJob.Status.REQUESTED,
                null, 0, 0, null, null, NOW, NOW, null, null, 0);
        jobs.insert(initial);

        FailOnceCheckpointRepository faultingJobs = new FailOnceCheckpointRepository(jobs);
        var firstWorker = worker(faultingJobs, new FilesystemWarehouseRecoveryAuditArtifactStore(artifactRoot));
        assertThrows(WarehouseRecoveryAuditPersistenceException.class, () -> firstWorker.runOne("worker-a"));

        var afterCrash = jobs.find(initial.tenantId(), exportId).orElseThrow();
        assertEquals(WarehouseRecoveryAuditExportJob.Status.RUNNING, afterCrash.status());
        assertEquals(0, afterCrash.exportedByteCount(), "PostgreSQL checkpoint must remain unchanged");
        assertTrue(afterCrash.leaseUntil().isAfter(NOW));

        // Model process death and lease expiry without sleeping; the database still has offset zero.
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE warehouse_recovery_audit_export_job SET lease_until=?, updated_at=? WHERE tenant_id=? AND export_id=?")) {
            statement.setTimestamp(1, Timestamp.from(NOW.minusSeconds(1)));
            statement.setTimestamp(2, Timestamp.from(NOW));
            statement.setString(3, initial.tenantId());
            statement.setString(4, exportId);
            assertEquals(1, statement.executeUpdate());
        }

        var secondWorker = worker(jobs, new FilesystemWarehouseRecoveryAuditArtifactStore(artifactRoot));
        var completed = secondWorker.runOne("worker-b").orElseThrow();

        assertEquals(WarehouseRecoveryAuditExportJob.Status.COMPLETED, completed.status());
        assertTrue(completed.exportedByteCount() > 0);
        assertEquals(completed.exportedByteCount(),
                new FilesystemWarehouseRecoveryAuditArtifactStore(artifactRoot)
                        .inspect(initial.tenantId(), exportId).byteSize());
        var manifest = manifests.find(initial.tenantId(), exportId).orElseThrow();
        assertEquals(completed.exportedByteCount(), manifest.byteSize());
        assertEquals(completed.exportedEventCount(), manifest.eventCount());
        assertEquals(completed.artifactLocation(), manifest.artifactLocation());
        assertEquals(1, count("warehouse_recovery_audit_archive_manifest", exportId));
        assertTrue(count("warehouse_recovery_audit_export_access", exportId) >= 2,
                "claim/resume and verification access events should be durable");
    }

    private WarehouseRecoveryAuditExportWorker worker(WarehouseRecoveryAuditExportJobRepository jobRepository,
            WarehouseRecoveryAuditArtifactStore artifactStore) {
        WarehouseExecutionAttemptRepository operationsRepository = (WarehouseExecutionAttemptRepository)
                java.lang.reflect.Proxy.newProxyInstance(WarehouseExecutionAttemptRepository.class.getClassLoader(),
                new Class<?>[]{WarehouseExecutionAttemptRepository.class}, (proxy, method, args) -> {
                    if (method.getName().equals("pageOperatorAuditEvents"))
                        return new WarehouseRecoveryAuditPage(List.of(), null);
                    if (method.getName().equals("toString")) return "PostgresFilesystemRecoveryTestRepository";
                    throw new UnsupportedOperationException(method.getName());
                });
        return new WarehouseRecoveryAuditExportWorker(new WarehouseRecoveryOperationsService(operationsRepository),
                jobRepository, access, manifests, artifactStore, new WarehouseRecoveryAuditArchiveVerifier(),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(2), 100);
    }

    private int count(String table, String exportId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE export_id=?")) {
            statement.setString(1, exportId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static Connection connect(String url, String user, String password) throws SQLException {
        if (user == null || user.isBlank()) return DriverManager.getConnection(url);
        return DriverManager.getConnection(url, user, password == null ? "" : password);
    }

    private static final class FailOnceCheckpointRepository implements WarehouseRecoveryAuditExportJobRepository {
        private final WarehouseRecoveryAuditExportJobRepository delegate;
        private boolean fail = true;
        private FailOnceCheckpointRepository(WarehouseRecoveryAuditExportJobRepository delegate) { this.delegate = delegate; }
        @Override public void insert(WarehouseRecoveryAuditExportJob job) { delegate.insert(job); }
        @Override public Optional<WarehouseRecoveryAuditExportJob> find(String tenantId, String exportId) {
            return delegate.find(tenantId, exportId);
        }
        @Override public boolean update(WarehouseRecoveryAuditExportJob job, long expectedVersion) {
            if (fail && job.status() == WarehouseRecoveryAuditExportJob.Status.RUNNING
                    && job.exportedByteCount() > 0) {
                fail = false;
                throw new WarehouseRecoveryAuditPersistenceException("Injected checkpoint outage",
                        new SQLException("injected checkpoint failure"));
            }
            return delegate.update(job, expectedVersion);
        }
        @Override public Optional<WarehouseRecoveryAuditExportJob> claimNext(String workerId, Instant now, Instant leaseUntil) {
            return delegate.claimNext(workerId, now, leaseUntil);
        }
    }

    private static final class SchemaDataSource implements DataSource {
        private final String url, user, password, schema;
        private SchemaDataSource(String url, String user, String password, String schema) {
            this.url = url; this.user = user; this.password = password; this.schema = schema;
        }
        @Override public Connection getConnection() throws SQLException {
            Connection connection = connect(url, user, password);
            try { connection.setSchema(schema); return connection; }
            catch (SQLException e) { connection.close(); throw e; }
        }
        @Override public Connection getConnection(String username, String password) throws SQLException {
            Connection connection = DriverManager.getConnection(url, username, password);
            try { connection.setSchema(schema); return connection; }
            catch (SQLException e) { connection.close(); throw e; }
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
