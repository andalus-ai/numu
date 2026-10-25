package tech.kayys.numou.courier.adapter.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class CourierOutboxOperationsPostgresIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("numou_courier")
            .withUsername("courier")
            .withPassword("courier");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void publicationAuditIsAppendOnlyAndTenantScoped() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID eventId = insertOutboxEvent(tenant);
        insertPublication(tenant, eventId, "integration-test");

        try (Connection c = connection(); PreparedStatement audit = c.prepareStatement("""
                INSERT INTO courier_event_publication_audit
                  (audit_id, tenant_id, event_id, destination, action, actor_reference,
                   reason, previous_status, previous_attempt_count)
                VALUES (?, ?, ?, ?, 'REPLAY_REQUESTED', ?, ?, 'DEAD', 12)
                """)) {
            audit.setObject(1, UUID.randomUUID());
            audit.setObject(2, tenant);
            audit.setObject(3, eventId);
            audit.setString(4, "integration-test");
            audit.setString(5, "ops@example.invalid");
            audit.setString(6, "integration replay test");
            assertEquals(1, audit.executeUpdate());
        }

        try (Connection c = connection(); PreparedStatement delete = c.prepareStatement("""
                DELETE FROM courier_event_publication_audit
                WHERE tenant_id = ? AND event_id = ?
                """)) {
            delete.setObject(1, tenant);
            delete.setObject(2, eventId);
            assertThrows(SQLException.class, delete::executeUpdate);
        }

        try (Connection c = connection(); PreparedStatement query = c.prepareStatement("""
                SELECT count(*) FROM courier_event_publication_audit
                WHERE tenant_id = ? AND event_id = ? AND destination = ?
                """)) {
            query.setObject(1, tenant);
            query.setObject(2, eventId);
            query.setString(3, "integration-test");
            try (ResultSet rs = query.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    void concurrentWorkersSkipRowsAlreadyLockedByAnotherTransaction() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID first = insertOutboxEvent(tenant);
        UUID second = insertOutboxEvent(tenant);
        insertPublication(tenant, first, "concurrent-test");
        insertPublication(tenant, second, "concurrent-test");

        try (Connection workerA = connection(); Connection workerB = connection()) {
            workerA.setAutoCommit(false);
            workerB.setAutoCommit(false);
            try (PreparedStatement lock = workerA.prepareStatement("""
                    SELECT event_id FROM courier_event_publications
                    WHERE tenant_id = ? AND destination = ?
                    ORDER BY event_id LIMIT 1 FOR UPDATE
                    """)) {
                lock.setObject(1, tenant);
                lock.setString(2, "concurrent-test");
                try (ResultSet rs = lock.executeQuery()) {
                    assertTrue(rs.next());
                }
            }

            try (PreparedStatement claim = workerB.prepareStatement("""
                    SELECT event_id FROM courier_event_publications
                    WHERE tenant_id = ? AND destination = ? AND status = 'PENDING'
                    ORDER BY event_id FOR UPDATE SKIP LOCKED
                    """)) {
                claim.setObject(1, tenant);
                claim.setString(2, "concurrent-test");
                try (ResultSet rs = claim.executeQuery()) {
                    assertTrue(rs.next(), "worker B should claim the unlocked row");
                    assertNotEquals(first, rs.getObject(1, UUID.class));
                    assertFalse(rs.next(), "worker B must not see the row locked by worker A");
                }
            } finally {
                workerB.rollback();
                workerA.rollback();
            }
        }
    }

    @Test
    void publicationForeignKeyRejectsMissingOutboxEvent() throws Exception {
        try (Connection c = connection(); PreparedStatement insert = c.prepareStatement("""
                INSERT INTO courier_event_publications (tenant_id, event_id, destination)
                VALUES (?, ?, ?)
                """)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, UUID.randomUUID());
            insert.setString(3, "missing-event");
            assertThrows(SQLException.class, insert::executeUpdate);
        }
    }

    private static UUID insertOutboxEvent(UUID tenant) throws SQLException {
        UUID eventId = UUID.randomUUID();
        try (Connection c = connection(); PreparedStatement insert = c.prepareStatement("""
                INSERT INTO courier_event_outbox
                  (tenant_id, id, event_type, aggregate_type, aggregate_reference, occurred_at, payload)
                VALUES (?, ?, 'courier.test.v1', 'shipment', ?, now(), CAST(? AS jsonb))
                """)) {
            insert.setObject(1, tenant);
            insert.setObject(2, eventId);
            insert.setString(3, "SHIP-" + eventId);
            insert.setString(4, "{\"eventId\":\"" + eventId + "\"}");
            assertEquals(1, insert.executeUpdate());
        }
        return eventId;
    }

    private static void insertPublication(UUID tenant, UUID eventId, String destination) throws SQLException {
        try (Connection c = connection(); PreparedStatement insert = c.prepareStatement("""
                INSERT INTO courier_event_publications (tenant_id, event_id, destination)
                VALUES (?, ?, ?)
                """)) {
            insert.setObject(1, tenant);
            insert.setObject(2, eventId);
            insert.setString(3, destination);
            assertEquals(1, insert.executeUpdate());
        }
    }

    private static Connection connection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
