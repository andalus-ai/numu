package tech.kayys.numou.queuemanagement.postgres;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.queuemanagement.spi.QueueOperationalSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(QueuePostgresQuarkusTestResource.class)
class QueuePostgresOperationalHealthQuarkusTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_TENANT = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Inject QueuePostgresOperationalHealthAdapter health;
    @Inject QueuePostgresOutboxDeliveryAdapter delivery;
    @Inject AgroalDataSource dataSource;

    @Test
    void snapshotSeparatesDueLeasedExpiredAndDeadLetteredEventsPerTenant() throws Exception {
        UUID leasedId = insertEvent();
        UUID deadId = insertEvent();

        var leased = delivery.claim(TENANT, "health-worker", 10, Duration.ofSeconds(30)).stream()
                .filter(event -> event.eventId().equals(leasedId)).findFirst().orElseThrow();
        var dead = delivery.claim(TENANT, "health-worker", 10, Duration.ofSeconds(30)).stream()
                .filter(event -> event.eventId().equals(deadId)).findFirst().orElseThrow();
        assertEquals(1, delivery.retry(TENANT, deadId, dead.leaseToken(), Duration.ZERO, 1, "terminal"),
                "retry outcome is validated below through dead-letter count");

        expireLease(leasedId);
        QueueOperationalSnapshot snapshot = health.snapshot(TENANT);

        assertEquals(TENANT, snapshot.tenantId());
        assertNotNull(snapshot.capturedAt());
        assertEquals(1, snapshot.pendingOutbox());
        assertEquals(0, snapshot.dueOutbox());
        assertEquals(0, snapshot.leasedOutbox());
        assertEquals(1, snapshot.expiredLeases());
        assertEquals(1, snapshot.deadLetteredOutbox());
        assertTrue(snapshot.oldestPendingAgeSeconds() >= 0);

        QueueOperationalSnapshot other = health.snapshot(OTHER_TENANT);
        assertEquals(0, other.pendingOutbox());
        assertEquals(0, other.deadLetteredOutbox());
        assertEquals(0, other.expiredLeases());
    }

    @Test
    void emptyTenantSnapshotReturnsZeroCounters() {
        QueueOperationalSnapshot snapshot = health.snapshot(OTHER_TENANT);
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
            setTenant(connection, TENANT);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO queue_outbox (event_id, tenant_id, event_type, schema_version, aggregate_type, "
                            + "aggregate_id, correlation_id, occurred_at, payload) "
                            + "VALUES (?, ?, 'QueueHealthTestEvent', 1, 'QUEUE_ENTRY', ?, ?, CURRENT_TIMESTAMP, '{}'::jsonb)")) {
                insert.setObject(1, id);
                insert.setObject(2, TENANT);
                insert.setString(3, "entry-" + id);
                insert.setString(4, "corr-" + id);
                insert.executeUpdate();
            }
        }
        return id;
    }

    private void expireLease(UUID eventId) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            setTenant(connection, TENANT);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE queue_outbox SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second' "
                            + "WHERE tenant_id = ? AND event_id = ?")) {
                update.setObject(1, TENANT);
                update.setObject(2, eventId);
                assertEquals(1, update.executeUpdate());
            }
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
