package tech.kayys.numou.queuemanagement.postgres;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.queuemanagement.spi.QueueOutboxDeliveryPort;
import tech.kayys.numou.queuemanagement.spi.QueueOutboxMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(QueuePostgresQuarkusTestResource.class)
class QueuePostgresOutboxDeliveryQuarkusTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Inject QueuePostgresOutboxDeliveryAdapter delivery;
    @Inject AgroalDataSource dataSource;

    @Test
    void claimsDueEventsAndAcknowledgesOnlyWithTheCurrentLeaseToken() throws Exception {
        UUID eventId = insertEvent();
        List<QueueOutboxMessage> claimed = delivery.claim(TENANT, "dispatcher-a", 10, Duration.ofSeconds(30));
        QueueOutboxMessage event = claimed.stream().filter(item -> item.eventId().equals(eventId))
                .findFirst().orElseThrow();

        assertEquals(1, event.attemptCount());
        assertEquals("dispatcher-a", event.leaseOwner());
        assertFalse(delivery.acknowledge(TENANT, eventId, UUID.randomUUID()));
        assertTrue(delivery.acknowledge(TENANT, eventId, event.leaseToken()));
        assertFalse(delivery.acknowledge(TENANT, eventId, event.leaseToken()));
    }

    @Test
    void retrySchedulesAnotherAttemptAndDeadLettersAtTheConfiguredLimit() throws Exception {
        UUID eventId = insertEvent();
        QueueOutboxMessage first = delivery.claim(TENANT, "dispatcher-b", 10, Duration.ofSeconds(30))
                .stream().filter(item -> item.eventId().equals(eventId)).findFirst().orElseThrow();

        assertEquals(QueueOutboxDeliveryPort.RetryOutcome.RESCHEDULED,
                delivery.retry(TENANT, eventId, first.leaseToken(), Duration.ZERO, 2, "temporary failure"));

        QueueOutboxMessage second = delivery.claim(TENANT, "dispatcher-b", 10, Duration.ofSeconds(30))
                .stream().filter(item -> item.eventId().equals(eventId)).findFirst().orElseThrow();
        assertEquals(2, second.attemptCount());
        assertEquals(QueueOutboxDeliveryPort.RetryOutcome.DEAD_LETTERED,
                delivery.retry(TENANT, eventId, second.leaseToken(), Duration.ofSeconds(5), 2, "terminal failure"));

        assertTrue(delivery.claim(TENANT, "dispatcher-c", 10, Duration.ofSeconds(30)).stream()
                .noneMatch(item -> item.eventId().equals(eventId)));
    }

    @Test
    void staleLeaseTokenCannotRetryAfterAnotherWorkerReclaimsEvent() throws Exception {
        UUID eventId = insertEvent();
        QueueOutboxMessage first = delivery.claim(TENANT, "dispatcher-old", 10, Duration.ofSeconds(1))
                .stream().filter(item -> item.eventId().equals(eventId)).findFirst().orElseThrow();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement expire = connection.prepareStatement(
                     "UPDATE queue_outbox SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second' "
                             + "WHERE tenant_id = ? AND event_id = ?")) {
            connection.setAutoCommit(false);
            setTenant(connection);
            expire.setObject(1, TENANT);
            expire.setObject(2, eventId);
            assertEquals(1, expire.executeUpdate());
            connection.commit();
        }

        QueueOutboxMessage second = delivery.claim(TENANT, "dispatcher-new", 10, Duration.ofSeconds(30))
                .stream().filter(item -> item.eventId().equals(eventId)).findFirst().orElseThrow();
        assertNotEquals(first.leaseToken(), second.leaseToken());
        assertEquals(QueueOutboxDeliveryPort.RetryOutcome.LEASE_LOST,
                delivery.retry(TENANT, eventId, first.leaseToken(), Duration.ZERO, 5, "stale worker"));
    }

    @Test
    void deadLettersAreInspectableAndReplayIsAuditedWithoutChangingEventIdentity() throws Exception {
        UUID eventId = insertEvent();
        QueueOutboxMessage first = delivery.claim(TENANT, "dispatcher-replay", 10, Duration.ofSeconds(30))
                .stream().filter(item -> item.eventId().equals(eventId)).findFirst().orElseThrow();
        assertEquals(QueueOutboxDeliveryPort.RetryOutcome.DEAD_LETTERED,
                delivery.retry(TENANT, eventId, first.leaseToken(), Duration.ZERO, 1, "broker rejected event"));

        var deadLetters = delivery.listDeadLetters(TENANT, 50);
        var deadLetter = deadLetters.stream().filter(item -> item.eventId().equals(eventId)).findFirst().orElseThrow();
        assertEquals(1, deadLetter.attemptCount());
        assertEquals(0, deadLetter.replayCount());
        assertEquals("broker rejected event", deadLetter.lastError());

        assertTrue(delivery.replayDeadLetter(TENANT, eventId, "operator-17", "corr-replay-1",
                "Broker configuration corrected"));
        assertFalse(delivery.replayDeadLetter(TENANT, eventId, "operator-17", "corr-replay-2",
                "Duplicate replay must be rejected"));
        assertTrue(delivery.listDeadLetters(TENANT, 50).stream()
                .noneMatch(item -> item.eventId().equals(eventId)));

        QueueOutboxMessage replayed = delivery.claim(TENANT, "dispatcher-replay-2", 10, Duration.ofSeconds(30))
                .stream().filter(item -> item.eventId().equals(eventId)).findFirst().orElseThrow();
        assertEquals(eventId, replayed.eventId());
        assertEquals(1, replayed.attemptCount());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT replay_count, total_attempt_count FROM queue_outbox WHERE tenant_id = ? AND event_id = ?")) {
            setTenant(connection);
            query.setObject(1, TENANT);
            query.setObject(2, eventId);
            try (var rs = query.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("replay_count"));
                assertEquals(2, rs.getInt("total_attempt_count"));
            }
            connection.commit();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT count(*) FROM queue_audit WHERE tenant_id = ? AND resource_id = ? "
                             + "AND action = 'OUTBOX_DEAD_LETTER_REPLAYED'")) {
            connection.setAutoCommit(false);
            setTenant(connection);
            query.setObject(1, TENANT);
            query.setString(2, eventId.toString());
            try (var rs = query.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
            connection.commit();
        }
    }

    @Test
    void deadLetterInspectionIsTenantScopedAndBounded() throws Exception {
        UUID eventId = insertEvent();
        QueueOutboxMessage event = delivery.claim(TENANT, "dispatcher-scope", 10, Duration.ofSeconds(30))
                .stream().filter(item -> item.eventId().equals(eventId)).findFirst().orElseThrow();
        delivery.retry(TENANT, eventId, event.leaseToken(), Duration.ZERO, 1, "terminal");

        assertTrue(delivery.listDeadLetters(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), 50)
                .stream().noneMatch(item -> item.eventId().equals(eventId)));
        assertThrows(IllegalArgumentException.class, () -> delivery.listDeadLetters(TENANT, 201));
    }

    private UUID insertEvent() throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO queue_outbox (event_id, tenant_id, event_type, schema_version, aggregate_type, "
                            + "aggregate_id, correlation_id, occurred_at, payload) "
                            + "VALUES (?, ?, 'QueueTestEvent', 1, 'QUEUE_ENTRY', ?, ?, CURRENT_TIMESTAMP, '{}'::jsonb)")) {
                insert.setObject(1, id);
                insert.setObject(2, TENANT);
                insert.setString(3, "entry-" + id);
                insert.setString(4, "corr-" + id);
                insert.executeUpdate();
            }
            connection.commit();
        }
        return id;
    }

    private static void setTenant(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('numou.tenant_id', ?, true)")) {
            statement.setString(1, TENANT.toString());
            statement.execute();
        }
    }
}
