package tech.kayys.numou.workspace.persistence.jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.workspace.domain.DatabaseRecordMigrationStage;
import tech.kayys.numou.workspace.domain.DatabaseSchemaMigrationJob;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcStructuredDatabaseMigrationRepositoryPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final List<String> MIGRATIONS = List.of(
            "V1__workspace_foundation.sql", "V2__workspace_page_links.sql",
            "V3__workspace_page_templates.sql", "V4__workspace_page_search_indexes.sql",
            "V5__workspace_case_insensitive_space_slugs.sql", "V6__workspace_template_name_integrity.sql",
            "V7__workspace_domain_invariants.sql", "V8__workspace_page_parent_invariant.sql",
            "V9__workspace_audit_invariants.sql", "V10__workspace_page_hierarchy_cycle_invariant.sql",
            "V11__workspace_revision_snapshot_integrity.sql", "V12__workspace_revision_blocks_immutable.sql",
            "V13__workspace_audit_action_semantics.sql", "V14__workspace_audit_resource_actor_integrity.sql",
            "V15__workspace_audit_history_append_only.sql", "V16__workspace_structured_databases.sql",
            "V17__workspace_database_views.sql", "V18__workspace_board_view_grouping.sql",
            "V19__workspace_saved_view_revision.sql", "V20__workspace_staged_schema_migrations.sql",
            "V21__workspace_schema_migration_worker_leases.sql",
            "V22__workspace_staged_migration_scope_integrity.sql");

    @Test
    void stagesTenantScopedBatchAtomicallyAndRejectsChangedSourceRevision() throws Exception {
        DataSource dataSource = dataSource();
        applyMigrations(dataSource);
        UUID tenant = UUID.randomUUID(), otherTenant = UUID.randomUUID(), workspace = UUID.randomUUID();
        UUID actor = UUID.randomUUID(), database = UUID.randomUUID(), table = UUID.randomUUID(), record = UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(120);
        insertSourceRows(dataSource, tenant, workspace, actor, database, table, record, now);

        JdbcStructuredDatabaseMigrationRepository repository = new JdbcStructuredDatabaseMigrationRepository(dataSource);
        UUID migrationId = UUID.randomUUID();
        DatabaseSchemaMigrationJob job = job(migrationId, tenant, database, table, actor, now, 1);
        assertTrue(repository.createMigrationJob(job));
        assertTrue(repository.findMigrationJob(tenant, migrationId).isPresent());
        assertEquals(Optional.empty(), repository.findMigrationJob(otherTenant, migrationId));

        UUID lease = UUID.randomUUID();
        Instant leaseUntil = now.plusSeconds(300);
        assertTrue(repository.claimMigrationJob(tenant, migrationId, 1, lease, leaseUntil, now));
        DatabaseRecordMigrationStage stage = new DatabaseRecordMigrationStage(
                tenant, migrationId, record, 1, "{\"new\":\"value\"}", now.plusSeconds(1));
        assertTrue(repository.stageMigrationBatch(tenant, migrationId, 2, lease, List.of(stage),
                record, true, leaseUntil, now.plusSeconds(2)));

        DatabaseSchemaMigrationJob ready = repository.findMigrationJob(tenant, migrationId).orElseThrow();
        assertEquals(DatabaseSchemaMigrationJob.Status.READY, ready.status());
        assertEquals(1, ready.stagedRecords());
        assertEquals(3, ready.revision());
        // Simulate a worker losing the acknowledgement after the batch committed.
        assertTrue(repository.stageMigrationBatch(tenant, migrationId, 2, lease, List.of(stage),
                record, true, leaseUntil, now.plusSeconds(2)));

        try (Connection c = dataSource.getConnection();
             PreparedStatement p = c.prepareStatement(
                     "SELECT record_data->>'old', record_data->>'new' FROM workspace_database_records WHERE tenant_id=? AND id=?")) {
            p.setObject(1, tenant); p.setObject(2, record);
            try (ResultSet r = p.executeQuery()) {
                assertTrue(r.next());
                assertEquals("value", r.getString(1));
                assertNull(r.getString(2), "staging must not mutate active record values");
            }
        }

        UUID conflictingJobId = UUID.randomUUID();
        assertTrue(repository.createMigrationJob(job(conflictingJobId, tenant, database, table, actor, now, 1)));
        UUID secondLease = UUID.randomUUID();
        assertTrue(repository.claimMigrationJob(tenant, conflictingJobId, 1, secondLease,
                leaseUntil, now.plusSeconds(3)));
        DatabaseRecordMigrationStage staleStage = new DatabaseRecordMigrationStage(
                tenant, conflictingJobId, record, 2, "{\"new\":\"stale\"}", now.plusSeconds(4));
        assertThrows(IllegalStateException.class, () -> repository.stageMigrationBatch(
                tenant, conflictingJobId, 2, secondLease, List.of(staleStage), record,
                true, leaseUntil, now.plusSeconds(5)));
        DatabaseSchemaMigrationJob stillRunning =
                repository.findMigrationJob(tenant, conflictingJobId).orElseThrow();
        assertEquals(DatabaseSchemaMigrationJob.Status.RUNNING, stillRunning.status());
        assertEquals(0, stillRunning.stagedRecords());
    }

    @Test
    void workerLeaseHasSingleWinnerAndExpiredLeaseCanBeReclaimed() throws Exception {
        DataSource dataSource = dataSource();
        applyMigrations(dataSource);
        UUID tenant = UUID.randomUUID(), workspace = UUID.randomUUID();
        UUID actor = UUID.randomUUID(), database = UUID.randomUUID(), table = UUID.randomUUID(), record = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-25T10:00:00Z");
        insertSourceRows(dataSource, tenant, workspace, actor, database, table, record, now);

        JdbcStructuredDatabaseMigrationRepository repository = new JdbcStructuredDatabaseMigrationRepository(dataSource);
        UUID migrationId = UUID.randomUUID();
        assertTrue(repository.createMigrationJob(job(migrationId, tenant, database, table, actor, now, 1)));

        UUID firstLease = UUID.randomUUID();
        UUID competingLease = UUID.randomUUID();
        Instant firstExpiry = now.plusSeconds(60);
        assertTrue(repository.claimMigrationJob(tenant, migrationId, 1, firstLease, firstExpiry, now));
        assertFalse(repository.claimMigrationJob(tenant, migrationId, 1, competingLease, firstExpiry, now.plusSeconds(1)),
                "a competing worker with a stale revision must not steal an active lease");

        DatabaseSchemaMigrationJob claimed = repository.findMigrationJob(tenant, migrationId).orElseThrow();
        assertEquals(2, claimed.revision());
        assertEquals(DatabaseSchemaMigrationJob.Status.RUNNING, claimed.status());
        Instant afterExpiry = firstExpiry.plusSeconds(1);
        assertEquals(1, repository.findResumableMigrationJobs(tenant, 10).size(),
                "an expired running job must be discoverable for recovery");
        UUID recoveryLease = UUID.randomUUID();
        assertTrue(repository.claimMigrationJob(tenant, migrationId, 2, recoveryLease,
                afterExpiry.plusSeconds(60), afterExpiry),
                "a worker may reclaim an expired lease using the latest job revision");
        assertFalse(repository.claimMigrationJob(tenant, migrationId, 2, firstLease,
                afterExpiry.plusSeconds(60), afterExpiry),
                "the expired lease token must not reclaim the job after recovery");

        DatabaseSchemaMigrationJob recovered = repository.findMigrationJob(tenant, migrationId).orElseThrow();
        assertEquals(3, recovered.revision());
        assertEquals(DatabaseSchemaMigrationJob.Status.RUNNING, recovered.status());
    }

    private DatabaseSchemaMigrationJob job(UUID id, UUID tenant, UUID database, UUID table, UUID actor,
                                           Instant now, long total) {
        return new DatabaseSchemaMigrationJob(id, tenant, database, table, actor, 1, 2,
                "Target table", "[{\"key\":\"new\"}]", DatabaseSchemaMigrationJob.Status.PENDING,
                20, total, 0, null, 1, null, null, now, now, null);
    }

    private void insertSourceRows(DataSource source, UUID tenant, UUID workspace, UUID actor, UUID database,
                                  UUID table, UUID record, Instant now) throws SQLException {
        try (Connection c = source.getConnection()) {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO workspace_workspaces(id,tenant_id,name,created_at) VALUES(?,?,?,?)")) {
                p.setObject(1, workspace); p.setObject(2, tenant); p.setString(3, "workspace");
                p.setTimestamp(4, java.sql.Timestamp.from(now)); p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO workspace_databases(id,tenant_id,workspace_id,name,created_by,created_at,updated_at) VALUES(?,?,?,?,?,?,?)")) {
                p.setObject(1, database); p.setObject(2, tenant); p.setObject(3, workspace);
                p.setString(4, "database"); p.setObject(5, actor);
                p.setTimestamp(6, java.sql.Timestamp.from(now)); p.setTimestamp(7, java.sql.Timestamp.from(now));
                p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO workspace_database_tables(id,tenant_id,database_id,name,schema_version,created_at,updated_at) VALUES(?,?,?,?,?,?,?)")) {
                p.setObject(1, table); p.setObject(2, tenant); p.setObject(3, database); p.setString(4, "table");
                p.setLong(5, 1); p.setTimestamp(6, java.sql.Timestamp.from(now)); p.setTimestamp(7, java.sql.Timestamp.from(now));
                p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO workspace_database_records(id,tenant_id,database_id,table_id,revision,record_data,created_at,updated_at) VALUES(?,?,?,?,1,'{\"old\":\"value\"}'::jsonb,?,?)")) {
                p.setObject(1, record); p.setObject(2, tenant); p.setObject(3, database); p.setObject(4, table);
                p.setTimestamp(5, java.sql.Timestamp.from(now)); p.setTimestamp(6, java.sql.Timestamp.from(now));
                p.executeUpdate();
            }
        }
    }

    private void applyMigrations(DataSource source) throws IOException, SQLException {
        try (Connection c = source.getConnection()) {
            for (String filename : MIGRATIONS) {
                String resource = "db/migration/" + filename;
                String sql;
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
                    assertNotNull(in, "Missing migration resource: " + resource);
                    sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                for (String statementSql : JdbcWorkspaceStorePostgresTest.splitSqlStatements(sql)) {
                    try (Statement statement = c.createStatement()) { statement.execute(statementSql); }
                }
            }
        }
    }

    private DataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }
}
