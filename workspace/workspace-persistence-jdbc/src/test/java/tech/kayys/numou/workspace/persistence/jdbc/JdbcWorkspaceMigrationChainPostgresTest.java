package tech.kayys.numou.workspace.persistence.jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcWorkspaceMigrationChainPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final List<String> MIGRATIONS = List.of(
            "V1__workspace_foundation.sql",
            "V2__workspace_page_links.sql",
            "V3__workspace_page_templates.sql",
            "V4__workspace_page_search_indexes.sql",
            "V5__workspace_case_insensitive_space_slugs.sql",
            "V6__workspace_template_name_integrity.sql",
            "V7__workspace_domain_invariants.sql",
            "V8__workspace_page_parent_invariant.sql",
            "V9__workspace_audit_invariants.sql",
            "V10__workspace_page_hierarchy_cycle_invariant.sql",
            "V11__workspace_revision_snapshot_integrity.sql",
            "V12__workspace_revision_blocks_immutable.sql",
            "V13__workspace_audit_action_semantics.sql",
            "V14__workspace_audit_resource_actor_integrity.sql",
            "V15__workspace_audit_history_append_only.sql",
            "V16__workspace_structured_databases.sql",
            "V17__workspace_database_views.sql",
            "V18__workspace_board_view_grouping.sql",
            "V19__workspace_saved_view_revision.sql",
            "V20__workspace_staged_schema_migrations.sql",
            "V21__workspace_schema_migration_worker_leases.sql",
            "V22__workspace_staged_migration_scope_integrity.sql",
            "V23__workspace_page_comment_threads.sql"
    );

    @Test
    void appliesEveryWorkspaceMigrationInVersionOrderAndCreatesStructuredSchema()
            throws SQLException, IOException {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection()) {
            for (String migration : MIGRATIONS) {
                String sql = readMigration(migration);
                for (String statementSql : JdbcWorkspaceStorePostgresTest.splitSqlStatements(sql)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(statementSql);
                    }
                }
            }
        }

        try (Connection connection = dataSource.getConnection()) {
            assertTrue(tableExists(connection, "workspace_workspaces"));
            assertTrue(tableExists(connection, "workspace_databases"));
            assertTrue(tableExists(connection, "workspace_database_tables"));
            assertTrue(tableExists(connection, "workspace_database_properties"));
            assertTrue(tableExists(connection, "workspace_database_records"));
            assertTrue(tableExists(connection, "workspace_database_views"));
            assertTrue(tableExists(connection, "workspace_database_schema_migrations"));
            assertTrue(tableExists(connection, "workspace_database_schema_migration_records"));
            assertTrue(columnExists(connection, "workspace_database_schema_migrations", "lease_token"));
            assertTrue(columnExists(connection, "workspace_database_schema_migrations", "lease_until"));
            assertTrue(indexExists(connection, "ix_workspace_schema_migrations_expired_lease"));
            assertTrue(columnExists(connection, "workspace_database_schema_migration_records", "database_id"));
            assertTrue(columnExists(connection, "workspace_database_schema_migration_records", "table_id"));
            assertTrue(constraintExists(connection, "fk_workspace_schema_migration_record_job_scope"));
            assertTrue(constraintExists(connection, "fk_workspace_schema_migration_record_source_scope"));
            assertTrue(indexExists(connection, "ix_workspace_schema_migration_records_table"));
            assertTrue(tableExists(connection, "workspace_page_comment_threads"));
            assertTrue(tableExists(connection, "workspace_page_comments"));
            assertTrue(indexExists(connection, "uq_workspace_page_comment_root"));
            assertTrue(indexExists(connection, "ix_workspace_page_comments_thread"));
            assertTrue(constraintExists(connection, "trg_workspace_page_comments_append_only"));
            assertTrue(columnExists(connection, "workspace_database_views", "group_by_property"));
            assertTrue(columnExists(connection, "workspace_database_views", "revision"));
            assertTrue(constraintExists(connection, "ck_workspace_database_view_revision_positive"));
            assertTrue(constraintExists(connection, "ck_workspace_database_view_group_by_property"));
            assertTrue(indexExists(connection, "ix_workspace_database_views_owner"));
            assertTrue(indexExists(connection, "ix_workspace_database_records_table"));
            assertTrue(indexExists(connection, "ix_workspace_database_records_data"));
            assertTrue(constraintExists(connection, "fk_workspace_database_record_table"));
            assertTrue(constraintExists(connection, "trg_workspace_audit_resource_integrity"));
        }
    }

    @Test
    void commentEntriesAreAppendOnlyWhileThreadResolutionRemainsMutable() throws SQLException, IOException {
        DataSource dataSource = dataSource();
        String schema = "workspace_comments_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection()) {
            try (Statement setup = connection.createStatement()) {
                setup.execute("CREATE SCHEMA " + schema);
                setup.execute("SET search_path TO " + schema);
            }
            for (String migration : MIGRATIONS) {
                String sql = readMigration(migration);
                for (String statementSql : JdbcWorkspaceStorePostgresTest.splitSqlStatements(sql)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(statementSql);
                    }
                }
            }

            UUID tenant = UUID.randomUUID();
            UUID workspace = UUID.randomUUID();
            UUID space = UUID.randomUUID();
            UUID page = UUID.randomUUID();
            UUID thread = UUID.randomUUID();
            UUID comment = UUID.randomUUID();
            UUID actor = UUID.randomUUID();
            Instant now = Instant.parse("2026-10-30T00:00:00Z");
            try (var insert = connection.prepareStatement(
                    "INSERT INTO workspace_workspaces(id,tenant_id,name,description,created_at) VALUES(?,?,?,?,?)")) {
                insert.setObject(1, workspace); insert.setObject(2, tenant); insert.setString(3, "Comments");
                insert.setString(4, ""); insert.setTimestamp(5, java.sql.Timestamp.from(now)); insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO workspace_spaces(id,tenant_id,workspace_id,name,slug,created_at) VALUES(?,?,?,?,?,?)")) {
                insert.setObject(1, space); insert.setObject(2, tenant); insert.setObject(3, workspace);
                insert.setString(4, "General"); insert.setString(5, "general");
                insert.setTimestamp(6, java.sql.Timestamp.from(now)); insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO workspace_pages(id,tenant_id,workspace_id,space_id,title,revision,created_at,updated_at) VALUES(?,?,?,?,?,1,?,?)")) {
                insert.setObject(1, page); insert.setObject(2, tenant); insert.setObject(3, workspace);
                insert.setObject(4, space); insert.setString(5, "Page");
                insert.setTimestamp(6, java.sql.Timestamp.from(now)); insert.setTimestamp(7, java.sql.Timestamp.from(now));
                insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO workspace_page_comment_threads(id,tenant_id,page_id,created_at,updated_at) VALUES(?,?,?,?,?)")) {
                insert.setObject(1, thread); insert.setObject(2, tenant); insert.setObject(3, page);
                insert.setTimestamp(4, java.sql.Timestamp.from(now)); insert.setTimestamp(5, java.sql.Timestamp.from(now));
                insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO workspace_page_comments(id,tenant_id,page_id,thread_id,author_id,body,created_at) VALUES(?,?,?,?,?,?,?)")) {
                insert.setObject(1, comment); insert.setObject(2, tenant); insert.setObject(3, page);
                insert.setObject(4, thread); insert.setObject(5, actor); insert.setString(6, "Review this");
                insert.setTimestamp(7, java.sql.Timestamp.from(now)); insert.executeUpdate();
            }
            try (var update = connection.prepareStatement(
                    "UPDATE workspace_page_comments SET body='rewritten' WHERE tenant_id=? AND id=?")) {
                update.setObject(1, tenant); update.setObject(2, comment);
                assertThrows(SQLException.class, update::executeUpdate);
            }
            try (var update = connection.prepareStatement(
                    "UPDATE workspace_page_comment_threads SET resolved=true,revision=2,resolved_by=?,resolved_at=?,updated_at=? WHERE tenant_id=? AND page_id=? AND id=? AND revision=1")) {
                update.setObject(1, actor); update.setTimestamp(2, java.sql.Timestamp.from(now));
                update.setTimestamp(3, java.sql.Timestamp.from(now)); update.setObject(4, tenant);
                update.setObject(5, page); update.setObject(6, thread);
                assertEquals(1, update.executeUpdate());
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

    private String readMigration(String filename) throws IOException {
        String resource = "db/migration/" + filename;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing migration resource: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?)")) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean(1);
            }
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?)")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean(1);
            }
        }
    }

    private boolean indexExists(Connection connection, String index) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = current_schema() AND indexname = ?)")) {
            statement.setString(1, index);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean(1);
            }
        }
    }

    private boolean constraintExists(Connection connection, String constraint) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = ?)")) {
            statement.setString(1, constraint);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                if (result.getBoolean(1)) return true;
            }
        }
        // PostgreSQL triggers are stored separately from constraints.
        try (var statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = ? AND NOT tgisinternal)")) {
            statement.setString(1, constraint);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean(1);
            }
        }
    }
}
