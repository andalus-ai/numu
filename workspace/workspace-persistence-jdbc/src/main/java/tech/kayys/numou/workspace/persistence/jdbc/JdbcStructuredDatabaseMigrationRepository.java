package tech.kayys.numou.workspace.persistence.jdbc;

import tech.kayys.numou.workspace.application.WorkspacePorts.StructuredDatabaseMigrationRepository;
import tech.kayys.numou.workspace.domain.DatabaseRecordMigrationStage;
import tech.kayys.numou.workspace.domain.DatabaseSchemaMigrationJob;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** PostgreSQL adapter for durable staged migration control-plane state. */
public final class JdbcStructuredDatabaseMigrationRepository implements StructuredDatabaseMigrationRepository {
    private final DataSource dataSource;

    public JdbcStructuredDatabaseMigrationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public boolean createMigrationJob(DatabaseSchemaMigrationJob job) {
        Objects.requireNonNull(job, "job");
        String sql = """
                INSERT INTO workspace_database_schema_migrations
                (id, tenant_id, database_id, table_id, requested_by, expected_schema_version,
                 target_schema_version, target_table_name, target_properties, status, batch_size,
                 total_records, staged_records, last_record_id, revision, failure_code, failure_message,
                 created_at, updated_at, completed_at, lease_token, lease_until)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, job.id()); p.setObject(2, job.tenantId()); p.setObject(3, job.databaseId());
            p.setObject(4, job.tableId()); p.setObject(5, job.requestedBy());
            p.setLong(6, job.expectedSchemaVersion()); p.setLong(7, job.targetSchemaVersion());
            p.setString(8, job.targetTableName()); p.setString(9, job.targetPropertiesJson());
            p.setString(10, job.status().name()); p.setInt(11, job.batchSize());
            p.setLong(12, job.totalRecords()); p.setLong(13, job.stagedRecords());
            p.setObject(14, job.lastRecordId()); p.setLong(15, job.revision());
            p.setString(16, job.failureCode()); p.setString(17, job.failureMessage());
            p.setTimestamp(18, Timestamp.from(job.createdAt())); p.setTimestamp(19, Timestamp.from(job.updatedAt()));
            p.setTimestamp(20, job.completedAt() == null ? null : Timestamp.from(job.completedAt()));
            return p.executeUpdate() == 1;
        } catch (SQLException e) {
            if (isConstraintViolation(e)) return false;
            throw failure("create schema migration job", e);
        }
    }

    @Override
    public Optional<DatabaseSchemaMigrationJob> findMigrationJob(UUID tenantId, UUID migrationId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement p = c.prepareStatement(selectColumns() + " WHERE tenant_id = ? AND id = ?")) {
            p.setObject(1, tenantId); p.setObject(2, migrationId);
            try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(readJob(r)) : Optional.empty(); }
        } catch (SQLException e) { throw failure("find schema migration job", e); }
    }

    @Override
    public List<DatabaseSchemaMigrationJob> findResumableMigrationJobs(UUID tenantId, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        String sql = selectColumns() + """
                 WHERE tenant_id = ? AND
                   (status = 'PENDING' OR (status = 'RUNNING' AND lease_until <= CURRENT_TIMESTAMP))
                 ORDER BY updated_at, id LIMIT ?
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, tenantId); p.setInt(2, limit);
            try (ResultSet r = p.executeQuery()) {
                List<DatabaseSchemaMigrationJob> jobs = new ArrayList<>();
                while (r.next()) jobs.add(readJob(r));
                return List.copyOf(jobs);
            }
        } catch (SQLException e) { throw failure("list resumable schema migration jobs", e); }
    }

    @Override
    public boolean claimMigrationJob(UUID tenantId, UUID migrationId, long expectedRevision,
                                     UUID leaseToken, Instant leaseUntil, Instant updatedAt) {
        Objects.requireNonNull(leaseToken, "leaseToken"); Objects.requireNonNull(leaseUntil, "leaseUntil");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!leaseUntil.isAfter(updatedAt)) throw new IllegalArgumentException("leaseUntil must be after updatedAt");
        String sql = """
                UPDATE workspace_database_schema_migrations
                   SET status = 'RUNNING', lease_token = ?, lease_until = ?,
                       revision = revision + 1, updated_at = ?
                 WHERE tenant_id = ? AND id = ? AND revision = ?
                   AND (status = 'PENDING' OR (status = 'RUNNING' AND lease_until <= ?))
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, leaseToken); p.setTimestamp(2, Timestamp.from(leaseUntil));
            p.setTimestamp(3, Timestamp.from(updatedAt)); p.setObject(4, tenantId);
            p.setObject(5, migrationId); p.setLong(6, expectedRevision);
            p.setTimestamp(7, Timestamp.from(updatedAt));
            return p.executeUpdate() == 1;
        } catch (SQLException e) { throw failure("claim schema migration job", e); }
    }

    @Override
    public boolean stageMigrationBatch(UUID tenantId, UUID migrationId, long expectedRevision, UUID leaseToken,
                                       List<DatabaseRecordMigrationStage> stages, UUID lastRecordId,
                                       boolean ready, Instant leaseUntil, Instant updatedAt) {
        Objects.requireNonNull(tenantId, "tenantId"); Objects.requireNonNull(migrationId, "migrationId");
        Objects.requireNonNull(leaseToken, "leaseToken"); Objects.requireNonNull(leaseUntil, "leaseUntil");
        Objects.requireNonNull(updatedAt, "updatedAt");
        List<DatabaseRecordMigrationStage> batch = stages == null ? List.of() : List.copyOf(stages);
        if (!leaseUntil.isAfter(updatedAt)) throw new IllegalArgumentException("leaseUntil must be after updatedAt");
        Set<UUID> ids = new HashSet<>();
        for (DatabaseRecordMigrationStage stage : batch) {
            if (!tenantId.equals(stage.tenantId()) || !migrationId.equals(stage.migrationId()))
                throw new IllegalArgumentException("every staged record must match the tenant and migration");
            if (!ids.add(stage.recordId())) throw new IllegalArgumentException("duplicate record in migration batch");
        }

        return inTransaction(c -> {
            DatabaseSchemaMigrationJob job;
            UUID currentLease;
            Instant currentLeaseUntil;
            try (PreparedStatement p = c.prepareStatement(selectColumns()
                    + " WHERE tenant_id = ? AND id = ? FOR UPDATE")) {
                p.setObject(1, tenantId); p.setObject(2, migrationId);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) return false;
                    job = readJob(r); currentLease = uuidOrNull(r, "lease_token");
                    Timestamp t = r.getTimestamp("lease_until");
                    currentLeaseUntil = t == null ? null : t.toInstant();
                }
            }
            if (job.revision() != expectedRevision || job.status() != DatabaseSchemaMigrationJob.Status.RUNNING
                    || !leaseToken.equals(currentLease) || currentLeaseUntil == null
                    || !currentLeaseUntil.isAfter(updatedAt)) {
                // A lost acknowledgement after a committed batch is safe to replay.
                return job.lastRecordId() != null && job.lastRecordId().equals(lastRecordId)
                        && stagedRowsMatch(c, tenantId, migrationId, batch);
            }
            if (batch.size() > job.batchSize()) throw new IllegalArgumentException("batch exceeds job batchSize");

            String insert = """
                    INSERT INTO workspace_database_schema_migration_records
                    (tenant_id, migration_id, record_id, expected_record_revision, migrated_record_data, staged_at)
                    VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)
                    ON CONFLICT (tenant_id, migration_id, record_id) DO NOTHING
                    """;
            try (PreparedStatement p = c.prepareStatement(insert)) {
                for (DatabaseRecordMigrationStage stage : batch) {
                    p.setObject(1, tenantId); p.setObject(2, migrationId); p.setObject(3, stage.recordId());
                    p.setLong(4, stage.expectedRecordRevision()); p.setString(5, stage.migratedRecordDataJson());
                    p.setTimestamp(6, Timestamp.from(stage.stagedAt())); p.addBatch();
                }
                p.executeBatch();
            }
            if (!stagedRowsMatch(c, tenantId, migrationId, batch))
                throw new IllegalStateException("staged migration row conflicts with an earlier batch result");

            long stagedCount;
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT count(*) FROM workspace_database_schema_migration_records WHERE tenant_id = ? AND migration_id = ?")) {
                p.setObject(1, tenantId); p.setObject(2, migrationId);
                try (ResultSet r = p.executeQuery()) { r.next(); stagedCount = r.getLong(1); }
            }
            if (stagedCount > job.totalRecords()) throw new IllegalStateException("staged count exceeds migration snapshot");
            if (ready && stagedCount != job.totalRecords())
                throw new IllegalArgumentException("migration cannot become READY before every record is staged");
            if (!ready && stagedCount == job.totalRecords() && job.totalRecords() > 0)
                throw new IllegalArgumentException("complete staging must transition the job to READY");

            String update = """
                    UPDATE workspace_database_schema_migrations
                       SET status = ?, staged_records = ?, last_record_id = ?, revision = revision + 1,
                           updated_at = ?, lease_token = ?, lease_until = ?
                     WHERE tenant_id = ? AND id = ? AND revision = ? AND status = 'RUNNING'
                       AND lease_token = ?
                    """;
            try (PreparedStatement p = c.prepareStatement(update)) {
                p.setString(1, ready ? "READY" : "RUNNING"); p.setLong(2, stagedCount);
                p.setObject(3, lastRecordId); p.setTimestamp(4, Timestamp.from(updatedAt));
                p.setObject(5, ready ? null : leaseToken);
                p.setTimestamp(6, ready ? null : Timestamp.from(leaseUntil));
                p.setObject(7, tenantId); p.setObject(8, migrationId); p.setLong(9, expectedRevision);
                p.setObject(10, leaseToken);
                if (p.executeUpdate() != 1) return false;
            }
            return true;
        });
    }

    private static boolean stagedRowsMatch(Connection c, UUID tenantId, UUID migrationId,
                                           List<DatabaseRecordMigrationStage> stages) throws SQLException {
        String sql = """
                SELECT expected_record_revision, migrated_record_data = CAST(? AS jsonb)
                  FROM workspace_database_schema_migration_records
                 WHERE tenant_id = ? AND migration_id = ? AND record_id = ?
                """;
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (DatabaseRecordMigrationStage stage : stages) {
                p.setString(1, stage.migratedRecordDataJson()); p.setObject(2, tenantId);
                p.setObject(3, migrationId); p.setObject(4, stage.recordId());
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next() || r.getLong(1) != stage.expectedRecordRevision() || !r.getBoolean(2)) return false;
                }
            }
        }
        return true;
    }

    private static String selectColumns() {
        return """
                SELECT id, tenant_id, database_id, table_id, requested_by, expected_schema_version,
                       target_schema_version, target_table_name, target_properties::text, status, batch_size,
                       total_records, staged_records, last_record_id, revision, failure_code, failure_message,
                       created_at, updated_at, completed_at, lease_token, lease_until
                  FROM workspace_database_schema_migrations
                """;
    }

    private static DatabaseSchemaMigrationJob readJob(ResultSet r) throws SQLException {
        Timestamp completed = r.getTimestamp("completed_at");
        return new DatabaseSchemaMigrationJob(r.getObject("id", UUID.class), r.getObject("tenant_id", UUID.class),
                r.getObject("database_id", UUID.class), r.getObject("table_id", UUID.class),
                r.getObject("requested_by", UUID.class), r.getLong("expected_schema_version"),
                r.getLong("target_schema_version"), r.getString("target_table_name"), r.getString("target_properties"),
                DatabaseSchemaMigrationJob.Status.valueOf(r.getString("status")), r.getInt("batch_size"),
                r.getLong("total_records"), r.getLong("staged_records"), uuidOrNull(r, "last_record_id"),
                r.getLong("revision"), r.getString("failure_code"), r.getString("failure_message"),
                r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant(),
                completed == null ? null : completed.toInstant());
    }

    private static UUID uuidOrNull(ResultSet r, String column) throws SQLException {
        return r.getObject(column, UUID.class);
    }

    private static boolean isConstraintViolation(SQLException e) {
        return e.getSQLState() != null && e.getSQLState().startsWith("23");
    }

    private static IllegalStateException failure(String operation, SQLException cause) {
        return new IllegalStateException("Unable to " + operation + " (SQLState " + cause.getSQLState() + ")", cause);
    }

    @FunctionalInterface private interface SqlWork<T> { T apply(Connection connection) throws SQLException; }

    private <T> T inTransaction(SqlWork<T> work) {
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                T result = work.apply(c);
                c.commit();
                return result;
            } catch (RuntimeException | SQLException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof SQLException sql) throw failure("persist migration batch", sql);
                throw (RuntimeException) e;
            } finally {
                try { c.setAutoCommit(autoCommit); } catch (SQLException ignored) { /* connection is closing */ }
            }
        } catch (SQLException e) { throw failure("persist migration batch", e); }
    }
}
