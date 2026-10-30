package tech.kayys.numou.queuemanagement.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class QueuePostgresSchemaIntegrationTest {
    private static final String RUNTIME_ROLE = "queue_runtime_test";
    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID QUEUE_A = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("queue_test").withUsername("queue_owner").withPassword("queue_owner");

    @BeforeAll
    static void migrateAndProvisionRuntimeRole() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();

        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + RUNTIME_ROLE + " LOGIN NOSUPERUSER NOBYPASSRLS");
            statement.execute("GRANT USAGE ON SCHEMA public TO " + RUNTIME_ROLE);
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + RUNTIME_ROLE);
        }
    }

    @Test
    void runtimeRoleIsTenantIsolatedAndMutationsRollbackAtomically() throws SQLException {
        insertQueueAsOwner(TENANT_A, QUEUE_A);

        try (Connection connection = runtimeConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL ROLE " + RUNTIME_ROLE);
            }
            setTenant(connection, TENANT_B);
            assertEquals(0, count(connection, "SELECT count(*) FROM queue_definition"));

            assertThrows(SQLException.class, () -> {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO queue_definition (tenant_id, queue_id, code, display_name, profile, ordering_policy, status) "
                                + "VALUES (?, ?, 'forbidden', 'Forbidden', 'WAITING_SERVICE', 'FIFO', 'ACTIVE')")) {
                    insert.setObject(1, TENANT_A);
                    insert.setObject(2, UUID.randomUUID());
                    insert.executeUpdate();
                }
            });
            connection.rollback();
        }

        try (Connection connection = runtimeConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL ROLE " + RUNTIME_ROLE);
            }
            setTenant(connection, TENANT_A);
            assertEquals(1, count(connection,
                    "SELECT count(*) FROM queue_definition WHERE queue_id = '" + QUEUE_A + "'"));

            try (PreparedStatement entry = connection.prepareStatement(
                    "INSERT INTO queue_entry (tenant_id, queue_id, entry_id, profile, source_capability, source_resource_type, source_resource_id, priority, status, entered_at, updated_at) "
                            + "VALUES (?, ?, ?, 'WAITING_SERVICE', 'field-service', 'visit', 'visit-1', 0, 'WAITING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                 PreparedStatement audit = connection.prepareStatement(
                    "INSERT INTO queue_audit (audit_id, tenant_id, actor_id, action, resource_type, resource_id, correlation_id, occurred_at) "
                            + "VALUES (?, ?, 'integration-test', 'QUEUE_JOINED', 'QUEUE_ENTRY', 'entry-1', 'corr-1', CURRENT_TIMESTAMP)");
                 PreparedStatement event = connection.prepareStatement(
                    "INSERT INTO queue_outbox (event_id, tenant_id, event_type, schema_version, aggregate_type, aggregate_id, correlation_id, occurred_at, payload) "
                            + "VALUES (?, ?, 'QueueEntryJoined', 1, 'QUEUE_ENTRY', 'entry-1', 'corr-1', CURRENT_TIMESTAMP, '{}'::jsonb)");
                 PreparedStatement key = connection.prepareStatement(
                    "INSERT INTO queue_idempotency (tenant_id, operation_scope, idempotency_key, request_hash, state) "
                            + "VALUES (?, 'queue.join', 'rollback-key', 'hash', 'IN_PROGRESS')")) {
                entry.setObject(1, TENANT_A);
                entry.setObject(2, QUEUE_A);
                entry.setObject(3, UUID.fromString("22222222-2222-2222-2222-222222222222"));
                entry.executeUpdate();
                audit.setObject(1, UUID.randomUUID());
                audit.setObject(2, TENANT_A);
                audit.executeUpdate();
                event.setObject(1, UUID.randomUUID());
                event.setObject(2, TENANT_A);
                event.executeUpdate();
                key.setObject(1, TENANT_A);
                key.executeUpdate();
            }
            connection.rollback();
        }

        try (Connection connection = ownerConnection()) {
            assertEquals(0, count(connection, "SELECT count(*) FROM queue_entry WHERE source_resource_id = 'visit-1'"));
            assertEquals(0, count(connection, "SELECT count(*) FROM queue_audit WHERE correlation_id = 'corr-1'"));
            assertEquals(0, count(connection, "SELECT count(*) FROM queue_outbox WHERE correlation_id = 'corr-1'"));
            assertEquals(0, count(connection, "SELECT count(*) FROM queue_idempotency WHERE idempotency_key = 'rollback-key'"));
        }
    }

    private static void insertQueueAsOwner(UUID tenant, UUID queue) throws SQLException {
        try (Connection connection = ownerConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO queue_definition (tenant_id, queue_id, code, display_name, profile, ordering_policy, status) "
                             + "VALUES (?, ?, 'main', 'Main Queue', 'WAITING_SERVICE', 'FIFO', 'ACTIVE')")) {
            insert.setObject(1, tenant);
            insert.setObject(2, queue);
            insert.executeUpdate();
        }
    }

    private static void setTenant(Connection connection, UUID tenant) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('numou.tenant_id', ?, true)")) {
            statement.setString(1, tenant.toString());
            statement.execute();
        }
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection runtimeConnection() throws SQLException {
        return ownerConnection();
    }
}
