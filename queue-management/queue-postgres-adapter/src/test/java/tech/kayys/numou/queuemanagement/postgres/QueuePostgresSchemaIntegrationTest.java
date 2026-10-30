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

    @Test
    void concurrentClaimSkipsTheEntryLockedByAnotherWorker() throws SQLException {
        UUID first = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID second = UUID.fromString("44444444-4444-4444-4444-444444444444");
        insertQueueAsOwner(TENANT_A, QUEUE_A);
        insertWaitingEntry(first, 10);
        insertWaitingEntry(second, 5);

        try (Connection workerOne = ownerConnection(); Connection workerTwo = ownerConnection()) {
            workerOne.setAutoCommit(false);
            workerTwo.setAutoCommit(false);
            setTenant(workerOne, TENANT_A);
            setTenant(workerTwo, TENANT_A);

            try (PreparedStatement lock = workerOne.prepareStatement(
                    "SELECT entry_id FROM queue_entry WHERE tenant_id = ? AND queue_id = ? AND entry_id = ? FOR UPDATE")) {
                lock.setObject(1, TENANT_A);
                lock.setObject(2, QUEUE_A);
                lock.setObject(3, first);
                try (ResultSet rs = lock.executeQuery()) {
                    if (!rs.next()) throw new AssertionError("Expected first waiting entry to exist");
                }
            }

            // This is the repository's claim CTE: a competing worker must skip the
            // highest-priority row while its lock is held and claim the next eligible row.
            UUID claimed = claimNextEntry(workerTwo);
            assertEquals(second, claimed);
            workerTwo.commit();
            workerOne.rollback();
        }
    }

    @Test
    void idempotencyKeyRejectsAChangedRequestHash() throws SQLException {
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, TENANT_A);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO queue_idempotency (tenant_id, operation_scope, idempotency_key, request_hash, state) "
                            + "VALUES (?, 'queue.join', 'same-key', ?, 'IN_PROGRESS') ON CONFLICT "
                            + "(tenant_id, operation_scope, idempotency_key) DO NOTHING")) {
                insert.setObject(1, TENANT_A);
                insert.setString(2, "hash-one");
                assertEquals(1, insert.executeUpdate());
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO queue_idempotency (tenant_id, operation_scope, idempotency_key, request_hash, state) "
                            + "VALUES (?, 'queue.join', 'same-key', ?, 'IN_PROGRESS') ON CONFLICT "
                            + "(tenant_id, operation_scope, idempotency_key) DO NOTHING")) {
                insert.setObject(1, TENANT_A);
                insert.setString(2, "hash-two");
                assertEquals(0, insert.executeUpdate());
            }
            try (PreparedStatement read = connection.prepareStatement(
                    "SELECT request_hash FROM queue_idempotency WHERE tenant_id = ? "
                            + "AND operation_scope = 'queue.join' AND idempotency_key = 'same-key' FOR UPDATE")) {
                read.setObject(1, TENANT_A);
                try (ResultSet rs = read.executeQuery()) {
                    if (!rs.next()) throw new AssertionError("Expected idempotency reservation");
                    assertEquals("hash-one", rs.getString(1));
                }
            }
            connection.rollback();
        }
    }

    @Test
    void outboxLeaseCanBeReclaimedAndStaleWorkerCannotAcknowledge() throws SQLException {
        UUID eventId = UUID.randomUUID();
        try (Connection connection = ownerConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO queue_outbox (event_id, tenant_id, event_type, schema_version, aggregate_type, "
                             + "aggregate_id, correlation_id, occurred_at, payload) "
                             + "VALUES (?, ?, 'QueueEntryJoined', 1, 'QUEUE_ENTRY', 'entry-lease', 'corr-lease', "
                             + "CURRENT_TIMESTAMP, '{}'::jsonb)")) {
            insert.setObject(1, eventId);
            insert.setObject(2, TENANT_A);
            insert.executeUpdate();
        }

        UUID firstToken = UUID.randomUUID();
        UUID secondToken = UUID.randomUUID();
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, TENANT_A);
            assertEquals(eventId, claimOutboxEvent(connection, eventId, firstToken, "worker-one"));

            try (PreparedStatement expire = connection.prepareStatement(
                    "UPDATE queue_outbox SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second' "
                            + "WHERE tenant_id = ? AND event_id = ?")) {
                expire.setObject(1, TENANT_A);
                expire.setObject(2, eventId);
                assertEquals(1, expire.executeUpdate());
            }
            assertEquals(eventId, claimOutboxEvent(connection, eventId, secondToken, "worker-two"));

            try (PreparedStatement staleAck = connection.prepareStatement(
                    "UPDATE queue_outbox SET published_at = CURRENT_TIMESTAMP, lease_owner = NULL, "
                            + "lease_token = NULL, lease_until = NULL WHERE tenant_id = ? AND event_id = ? "
                            + "AND lease_token = ? AND published_at IS NULL")) {
                staleAck.setObject(1, TENANT_A);
                staleAck.setObject(2, eventId);
                staleAck.setObject(3, firstToken);
                assertEquals(0, staleAck.executeUpdate(), "expired worker must not acknowledge a reclaimed event");
            }
            try (PreparedStatement ack = connection.prepareStatement(
                    "UPDATE queue_outbox SET published_at = CURRENT_TIMESTAMP, lease_owner = NULL, "
                            + "lease_token = NULL, lease_until = NULL WHERE tenant_id = ? AND event_id = ? "
                            + "AND lease_token = ? AND published_at IS NULL")) {
                ack.setObject(1, TENANT_A);
                ack.setObject(2, eventId);
                ack.setObject(3, secondToken);
                assertEquals(1, ack.executeUpdate(), "current lease holder must be able to acknowledge");
            }
            connection.commit();
        }
    }

    private static UUID claimOutboxEvent(Connection connection, UUID eventId, UUID leaseToken,
                                         String workerId) throws SQLException {
        String sql = "WITH candidate AS (SELECT event_id FROM queue_outbox WHERE tenant_id = ? "
                + "AND event_id = ? AND published_at IS NULL AND dead_lettered_at IS NULL "
                + "AND available_at <= CURRENT_TIMESTAMP "
                + "AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP) "
                + "FOR UPDATE SKIP LOCKED) "
                + "UPDATE queue_outbox o SET lease_owner = ?, lease_token = ?, "
                + "lease_until = CURRENT_TIMESTAMP + INTERVAL '30 seconds', "
                + "attempt_count = attempt_count + 1, last_attempt_at = CURRENT_TIMESTAMP "
                + "FROM candidate c WHERE o.event_id = c.event_id RETURNING o.event_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, TENANT_A);
            statement.setObject(2, eventId);
            statement.setString(3, workerId);
            statement.setObject(4, leaseToken);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        }
    }

    private static UUID claimNextEntry(Connection connection) throws SQLException {
        String sql = "WITH candidate AS (SELECT e.tenant_id, e.queue_id, e.entry_id, e.profile "
                + "FROM queue_entry e JOIN queue_definition qd ON qd.tenant_id = e.tenant_id AND qd.queue_id = e.queue_id "
                + "WHERE e.tenant_id = ? AND e.queue_id = ? AND e.status = 'WAITING' AND qd.status = 'ACTIVE' "
                + "AND ((? = 'CALL_SERVICE_ENTRY' AND e.profile = 'WAITING_SERVICE') "
                + "OR (? = 'OFFER_DISPATCH_ENTRY' AND e.profile = 'DISPATCH')) "
                + "ORDER BY CASE WHEN qd.ordering_policy = 'PRIORITY_THEN_ARRIVAL' THEN e.priority END DESC, "
                + "e.entered_at, e.entry_id LIMIT 1 FOR UPDATE OF e SKIP LOCKED) "
                + "UPDATE queue_entry e SET status = CASE WHEN ? = 'CALL_SERVICE_ENTRY' THEN 'CALLED' ELSE 'OFFERED' END, "
                + "called_at = CASE WHEN ? = 'CALL_SERVICE_ENTRY' THEN ? ELSE e.called_at END, "
                + "call_count = CASE WHEN ? = 'CALL_SERVICE_ENTRY' THEN e.call_count + 1 ELSE e.call_count END, "
                + "updated_at = ?, version = e.version + 1 FROM candidate c "
                + "WHERE e.tenant_id = c.tenant_id AND e.queue_id = c.queue_id AND e.entry_id = c.entry_id "
                + "RETURNING e.entry_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int i = 1;
            statement.setObject(i++, TENANT_A);
            statement.setObject(i++, QUEUE_A);
            statement.setString(i++, "CALL_SERVICE_ENTRY");
            statement.setString(i++, "CALL_SERVICE_ENTRY");
            statement.setString(i++, "CALL_SERVICE_ENTRY");
            statement.setString(i++, "CALL_SERVICE_ENTRY");
            statement.setTimestamp(i++, java.sql.Timestamp.from(java.time.Instant.now()));
            statement.setString(i++, "CALL_SERVICE_ENTRY");
            statement.setTimestamp(i, java.sql.Timestamp.from(java.time.Instant.now()));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new AssertionError("Expected a claimable queue entry");
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static void insertWaitingEntry(UUID entryId, int priority) throws SQLException {
        try (Connection connection = ownerConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO queue_entry (tenant_id, queue_id, entry_id, profile, source_capability, "
                             + "source_resource_type, source_resource_id, priority, status, entered_at, updated_at) "
                             + "VALUES (?, ?, ?, 'WAITING_SERVICE', 'integration', 'ticket', ?, ?, 'WAITING', "
                             + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            insert.setObject(1, TENANT_A);
            insert.setObject(2, QUEUE_A);
            insert.setObject(3, entryId);
            insert.setString(4, entryId.toString());
            insert.setInt(5, priority);
            insert.executeUpdate();
        }
    }

    private static void insertQueueAsOwner(UUID tenant, UUID queue) throws SQLException {
        try (Connection connection = ownerConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO queue_definition (tenant_id, queue_id, code, display_name, profile, ordering_policy, status) "
                             + "VALUES (?, ?, 'main', 'Main Queue', 'WAITING_SERVICE', 'FIFO', 'ACTIVE') "
                             + "ON CONFLICT (tenant_id, queue_id) DO NOTHING")) {
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
