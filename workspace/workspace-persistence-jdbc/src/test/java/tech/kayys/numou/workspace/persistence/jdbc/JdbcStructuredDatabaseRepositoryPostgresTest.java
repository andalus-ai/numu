package tech.kayys.numou.workspace.persistence.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry;
import tech.kayys.numou.workspace.domain.*;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcStructuredDatabaseRepositoryPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private final UUID tenant = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-24T08:00:00Z");
    private DataSource dataSource;
    private JdbcStructuredDatabaseRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        postgres.setURL(POSTGRES.getJdbcUrl());
        postgres.setUser(POSTGRES.getUsername());
        postgres.setPassword(POSTGRES.getPassword());
        dataSource = postgres;
        repository = new JdbcStructuredDatabaseRepository(dataSource);

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
            statement.execute("""
                    CREATE TABLE workspace_workspaces (
                      id UUID PRIMARY KEY,
                      tenant_id UUID NOT NULL,
                      name VARCHAR(200) NOT NULL,
                      CONSTRAINT uq_workspace_tenant_id UNIQUE (tenant_id, id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE workspace_audit_events (
                      id BIGSERIAL PRIMARY KEY,
                      tenant_id UUID NOT NULL,
                      actor_id UUID NOT NULL,
                      action VARCHAR(100) NOT NULL,
                      resource_type VARCHAR(100) NOT NULL,
                      resource_id UUID NOT NULL,
                      revision BIGINT NOT NULL,
                      summary TEXT NOT NULL DEFAULT '',
                      occurred_at TIMESTAMPTZ NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE workspace_databases (
                      id UUID PRIMARY KEY, tenant_id UUID NOT NULL, workspace_id UUID NOT NULL,
                      name VARCHAR(200) NOT NULL, created_by UUID NOT NULL,
                      created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
                      CONSTRAINT uq_workspace_database_tenant_id UNIQUE (tenant_id, id),
                      CONSTRAINT uq_workspace_database_owner UNIQUE (tenant_id, workspace_id, id),
                      CONSTRAINT uq_workspace_database_name UNIQUE (tenant_id, workspace_id, name),
                      CONSTRAINT fk_workspace_database_workspace FOREIGN KEY (tenant_id, workspace_id)
                        REFERENCES workspace_workspaces (tenant_id, id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE workspace_database_tables (
                      id UUID PRIMARY KEY, tenant_id UUID NOT NULL, database_id UUID NOT NULL,
                      name VARCHAR(200) NOT NULL, schema_version BIGINT NOT NULL CHECK (schema_version >= 1),
                      created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
                      CONSTRAINT uq_workspace_database_table_tenant_id UNIQUE (tenant_id, id),
                      CONSTRAINT uq_workspace_database_table_owner UNIQUE (tenant_id, database_id, id),
                      CONSTRAINT uq_workspace_database_table_name UNIQUE (tenant_id, database_id, name),
                      CONSTRAINT fk_workspace_database_table_owner FOREIGN KEY (tenant_id, database_id)
                        REFERENCES workspace_databases (tenant_id, id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE workspace_database_properties (
                      tenant_id UUID NOT NULL, database_id UUID NOT NULL, table_id UUID NOT NULL,
                      property_id UUID NOT NULL, property_key VARCHAR(63) NOT NULL, label VARCHAR(200) NOT NULL,
                      property_type VARCHAR(32) NOT NULL CHECK (property_type IN
                        ('TEXT', 'NUMBER', 'BOOLEAN', 'DATE', 'DATE_TIME', 'SELECT', 'MULTI_SELECT', 'RELATION')),
                      required BOOLEAN NOT NULL DEFAULT FALSE,
                      options JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(options) = 'array'),
                      related_table_id UUID NULL,
                      PRIMARY KEY (tenant_id, table_id, property_id),
                      CONSTRAINT uq_workspace_database_property_key UNIQUE (tenant_id, table_id, property_key),
                      CONSTRAINT fk_workspace_database_property_table FOREIGN KEY (tenant_id, database_id, table_id)
                        REFERENCES workspace_database_tables (tenant_id, database_id, id) ON DELETE CASCADE,
                      CONSTRAINT fk_workspace_database_property_relation FOREIGN KEY (tenant_id, database_id, related_table_id)
                        REFERENCES workspace_database_tables (tenant_id, database_id, id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE workspace_database_records (
                      id UUID PRIMARY KEY, tenant_id UUID NOT NULL, database_id UUID NOT NULL, table_id UUID NOT NULL,
                      revision BIGINT NOT NULL CHECK (revision >= 1),
                      record_data JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(record_data) = 'object'),
                      created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, archived_at TIMESTAMPTZ NULL,
                      CONSTRAINT uq_workspace_database_record_tenant_id UNIQUE (tenant_id, id),
                      CONSTRAINT fk_workspace_database_record_table FOREIGN KEY (tenant_id, database_id, table_id)
                        REFERENCES workspace_database_tables (tenant_id, database_id, id) ON DELETE CASCADE
                    )
                    """);
            try (var insert = connection.prepareStatement(
                    "INSERT INTO workspace_workspaces(id,tenant_id,name) VALUES(?,?,?)")) {
                insert.setObject(1, workspaceId);
                insert.setObject(2, tenant);
                insert.setString(3, "Integration workspace");
                insert.executeUpdate();
            }
        }
    }

    @Test
    void jsonbRoundTripPreservesCanonicalValuesAndRelations() {
        WorkspaceDatabase database = createDatabase();
        DatabaseTable targets = new DatabaseTable(UUID.randomUUID(), tenant, database.id(), "Targets", 1, List.of(
                property("name", DatabasePropertyType.TEXT, true, List.of(), null)));
        assertTrue(repository.saveTable(targets, audit(targets.id(), "DATABASE_TABLE", "DATABASE_TABLE_CREATED", 1)));
        DatabaseRecord target = DatabaseRecord.create(tenant, database.id(), targets,
                Map.of("name", "Target A"), now);
        assertTrue(repository.saveRecord(target, 0,
                audit(target.id(), "DATABASE_RECORD", "DATABASE_RECORD_CREATED", 1)));

        DatabaseTable tasks = new DatabaseTable(UUID.randomUUID(), tenant, database.id(), "Tasks", 1, List.of(
                property("title", DatabasePropertyType.TEXT, true, List.of(), null),
                property("amount", DatabasePropertyType.NUMBER, false, List.of(), null),
                property("enabled", DatabasePropertyType.BOOLEAN, false, List.of(), null),
                property("due_date", DatabasePropertyType.DATE, false, List.of(), null),
                property("updated_at", DatabasePropertyType.DATE_TIME, false, List.of(), null),
                property("status", DatabasePropertyType.SELECT, false, List.of("todo", "done"), null),
                property("labels", DatabasePropertyType.MULTI_SELECT, false, List.of("ops", "urgent"), null),
                property("targets", DatabasePropertyType.RELATION, false, List.of(), targets.id())));
        assertTrue(repository.saveTable(tasks, audit(tasks.id(), "DATABASE_TABLE", "DATABASE_TABLE_CREATED", 1)));

        DatabaseRecord task = DatabaseRecord.create(tenant, database.id(), tasks, Map.of(
                "title", "Ship release\nquoted: \"ok\"",
                "amount", new BigDecimal("12.340"),
                "enabled", true,
                "due_date", LocalDate.parse("2026-11-01"),
                "updated_at", Instant.parse("2026-10-24T08:30:00Z"),
                "status", "todo",
                "labels", List.of("ops", "urgent"),
                "targets", List.of(target.id())), now);
        assertTrue(repository.saveRecord(task, 0,
                audit(task.id(), "DATABASE_RECORD", "DATABASE_RECORD_CREATED", 1)));

        DatabaseRecord loaded = repository.findRecord(tenant, task.id()).orElseThrow();
        assertEquals(task.id(), loaded.id());
        assertEquals(1, loaded.revision());
        assertEquals("Ship release\nquoted: \"ok\"", loaded.values().get("title"));
        assertEquals(new BigDecimal("12.340"), loaded.values().get("amount"));
        assertEquals(Boolean.TRUE, loaded.values().get("enabled"));
        assertEquals(LocalDate.parse("2026-11-01"), loaded.values().get("due_date"));
        assertEquals(Instant.parse("2026-10-24T08:30:00Z"), loaded.values().get("updated_at"));
        assertEquals("todo", loaded.values().get("status"));
        assertEquals(List.of("ops", "urgent"), loaded.values().get("labels"));
        assertEquals(List.of(target.id()), loaded.values().get("targets"));
    }

    @Test
    void readsAreTenantScopedAndStaleUpdatesDoNotWriteAuditRows() throws SQLException {
        WorkspaceDatabase database = createDatabase();
        DatabaseTable table = new DatabaseTable(UUID.randomUUID(), tenant, database.id(), "Tasks", 1, List.of(
                property("title", DatabasePropertyType.TEXT, true, List.of(), null)));
        assertTrue(repository.saveTable(table, audit(table.id(), "DATABASE_TABLE", "DATABASE_TABLE_CREATED", 1)));
        DatabaseRecord initial = DatabaseRecord.create(tenant, database.id(), table, Map.of("title", "draft"), now);
        assertTrue(repository.saveRecord(initial, 0,
                audit(initial.id(), "DATABASE_RECORD", "DATABASE_RECORD_CREATED", 1)));

        assertTrue(repository.findRecord(UUID.randomUUID(), initial.id()).isEmpty());
        assertTrue(repository.findDatabase(UUID.randomUUID(), database.id()).isEmpty());
        long auditBefore = countAuditRows();

        DatabaseRecord updateA = initial.update(table, 1, Map.of("title", "ready"), now.plusSeconds(1));
        DatabaseRecord updateB = initial.update(table, 1, Map.of("title", "blocked"), now.plusSeconds(2));
        assertTrue(repository.saveRecord(updateA, 1,
                audit(updateA.id(), "DATABASE_RECORD", "DATABASE_RECORD_UPDATED", 2)));
        assertFalse(repository.saveRecord(updateB, 1,
                audit(updateB.id(), "DATABASE_RECORD", "DATABASE_RECORD_UPDATED", 2)));

        assertEquals("ready", repository.findRecord(tenant, initial.id()).orElseThrow().values().get("title"));
        assertEquals(auditBefore + 1, countAuditRows());
    }

    @Test
    void failedAuditInsertRollsBackRecordMutation() throws SQLException {
        WorkspaceDatabase database = createDatabase();
        DatabaseTable table = new DatabaseTable(UUID.randomUUID(), tenant, database.id(), "Tasks", 1, List.of(
                property("title", DatabasePropertyType.TEXT, true, List.of(), null)));
        assertTrue(repository.saveTable(table, audit(table.id(), "DATABASE_TABLE", "DATABASE_TABLE_CREATED", 1)));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE workspace_audit_events ADD CONSTRAINT reject_record_created CHECK (action <> 'DATABASE_RECORD_CREATED')");
        }

        DatabaseRecord record = DatabaseRecord.create(tenant, database.id(), table, Map.of("title", "must rollback"), now);
        assertFalse(repository.saveRecord(record, 0,
                audit(record.id(), "DATABASE_RECORD", "DATABASE_RECORD_CREATED", 1)));
        assertTrue(repository.findRecord(tenant, record.id()).isEmpty());
        assertEquals(0, countRecords());
    }

    private WorkspaceDatabase createDatabase() {
        WorkspaceDatabase database = WorkspaceDatabase.create(tenant, workspaceId, actor, "Structured data", now);
        assertTrue(repository.saveDatabase(database,
                audit(database.id(), "DATABASE", "DATABASE_CREATED", 1)));
        return database;
    }

    private DatabaseProperty property(String key, DatabasePropertyType type, boolean required,
                                      List<String> options, UUID relatedTableId) {
        return new DatabaseProperty(UUID.randomUUID(), key, key, type, required, options, relatedTableId);
    }

    private AuditEntry audit(UUID resourceId, String resourceType, String action, long revision) {
        return new AuditEntry(tenant, actor, action, resourceType, resourceId, revision, "integration test", now);
    }

    private long countAuditRows() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM workspace_audit_events")) {
            result.next();
            return result.getLong(1);
        }
    }

    private long countRecords() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM workspace_database_records")) {
            result.next();
            return result.getLong(1);
        }
    }
}
