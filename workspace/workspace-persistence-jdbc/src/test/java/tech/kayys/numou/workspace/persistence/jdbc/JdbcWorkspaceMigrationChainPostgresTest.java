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
import java.util.List;

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
            "V22__workspace_staged_migration_scope_integrity.sql"
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
