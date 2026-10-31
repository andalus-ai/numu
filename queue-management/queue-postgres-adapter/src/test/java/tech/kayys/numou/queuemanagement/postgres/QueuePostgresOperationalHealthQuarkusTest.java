package tech.kayys.numou.queuemanagement.postgres;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.queuemanagement.spi.QueueOperationalSnapshot;
import tech.kayys.numou.queuemanagement.spi.QueueOutboxDeliveryPort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(QueuePostgresQuarkusTestResource.class)
class QueuePostgresOperationalHealthQuarkusTest {
    private UUID tenantId;
    private UUID otherTenantId;

    @BeforeEach
    void initializeTenantScope() {
        tenantId = UUID.randomUUID();
        otherTenantId = UUID.randomUUID();
    }

    @Inject QueuePostgresOperationalHealthAdapter health;
    @Inject QueuePostgresOutboxDeliveryAdapter delivery;
    @Inject AgroalDataSource dataSource;

    @Test
    void snapshotSeparatesDueLeasedExpiredAndDeadLetteredEventsPerTenant() throws Exception {
        UUID leasedId = insertEvent();
        UUID deadId = insertEvent();

        var claimed = delivery.claim(tenantId, "health-worker", 10, Duration.ofSeconds(30));
        var leased = claimed.stream().filter(event -> event.eventId().equals(leasedId)).findFirst().orElseThrow();
        var dead = claimed.stream().filter(event -> event.eventId().equals(deadId)).findFirst().orElseThrow();
        assertEquals(QueueOutboxDeliveryPort.RetryOutcome.DEAD_LETTERED,
                delivery.retry(tenantId, deadId, dead.leaseToken(), Duration.ZERO, 1, "terminal"));

        expireLease(leasedId);
        QueueOperationalSnapshot snapshot = health.snapshot(tenantId);

        assertEquals(tenantId, snapshot.tenantId());
        assertNotNull(snapshot.capturedAt());
        assertEquals(1, snapshot.pendingOutbox());
        assertEquals(1, snapshot.dueOutbox());
        assertEquals(0, snapshot.leasedOutbox());
        assertEquals(1, snapshot.expiredLeases());
        assertEquals(1, snapshot.deadLetteredOutbox());
        assertTrue(snapshot.oldestPendingAgeSeconds() >= 0);

        QueueOperationalSnapshot other = health.snapshot(otherTenantId);
        assertEquals(0, other.pendingOutbox());
        assertEquals(0, other.deadLetteredOutbox());
        assertEquals(0, other.expiredLeases());
    }

    @Test
    void emptyTenantSnapshotReturnsZeroCounters() {
        QueueOperationalSnapshot snapshot = health.snapshot(otherTenantId);
        assertEquals(0, snapshot.waitingEntries());
        assertEquals(0, snapshot.activeEntries());
        assertEquals(0, snapshot.pendingOutbox());
        assertEquals(0, snapshot.dueOutbox());
        assertEquals(0, snapshot.leasedOutbox());
        assertEquals(0, snapshot.expiredLeases());
        assertEquals(0, snapshot.deadLetteredOutbox());
        assertEquals(0, snapshot.oldestPendingAgeSeconds());
    }

    private UUID insertEvent() throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, tenantId);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO queue_outbox (event_id, tenant_id, event_type, schema_version, aggregate_type, "
                            + "aggregate_id, correlation_id, occurred_at, payload) "
                            + "VALUES (?, ?, 'QueueHealthTestEvent', 1, 'QUEUE_ENTRY', ?, ?, CURRENT_TIMESTAMP, '{}'::jsonb)")) {
                insert.setObject(1, id);
                insert.setObject(2, tenantId);
                insert.setString(3, "entry-" + id);
                insert.setString(4, "corr-" + id);
                insert.executeUpdate();
            }
            connection.commit();
        }
        return id;
    }

    private void expireLease(UUID eventId) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, tenantId);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE queue_outbox SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second' "
                            + "WHERE tenant_id = ? AND event_id = ?")) {
                update.setObject(1, tenantId);
                update.setObject(2, eventId);
                assertEquals(1, update.executeUpdate());
            }
            connection.commit();
        }
    }

    private static void setTenant(Connection connection, UUID tenantId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('numou.tenant_id', ?, true)")) {
            statement.setString(1, tenantId.toString());
            statement.execute();
        }
    }
}
