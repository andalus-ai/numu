package tech.kayys.numou.courier.adapter.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class CourierOutboxEndToEndTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("numou_courier")
            .withUsername("courier")
            .withPassword("courier");

    static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load().migrate();
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        dataSource = source;
    }

    @Test
    void publishesEnvelopeAndAcknowledgesOnlyAfterTransportReturns() throws Exception {
        EventRef ref = insertEvent("success");
        AtomicReference<CourierPublishedEvent> received = new AtomicReference<>();

        int accepted = new JdbcCourierOutboxPublisher(dataSource).publishBatch(
                ref.destination(), "worker-success", 10,
                (destination, event) -> {
                    assertEquals(ref.destination(), destination);
                    received.set(event);
                });

        assertEquals(1, accepted);
        assertNotNull(received.get());
        assertEquals(ref.tenantId(), received.get().tenantId());
        assertEquals(ref.eventId(), received.get().eventId());
        assertEquals("courier.test.v1", received.get().eventType());
        assertTrue(received.get().payloadJson().contains(ref.eventId().toString()));
        assertEquals("PUBLISHED", status(ref, "status"));
        assertEquals(1, statusInt(ref, "attempt_count"));
        assertNull(status(ref, "lease_owner"));
        assertNotNull(status(ref, "published_at"));
    }

    @Test
    void retriesFailureThenPublishesWhenAvailableAgain() throws Exception {
        EventRef ref = insertEvent("retry");
        JdbcCourierOutboxPublisher relay = new JdbcCourierOutboxPublisher(dataSource);

        assertEquals(0, relay.publishBatch(ref.destination(), "worker-retry", 10,
                (destination, event) -> { throw new IllegalStateException("simulated transport outage"); }));
        assertEquals("PENDING", status(ref, "status"));
        assertEquals(1, statusInt(ref, "attempt_count"));
        assertTrue(status(ref, "last_error").contains("simulated transport outage"));
        assertNull(status(ref, "lease_owner"));

        execute("UPDATE courier_event_publications SET available_at = now() WHERE tenant_id = ? AND event_id = ? AND destination = ?",
                ref);
        assertEquals(1, relay.publishBatch(ref.destination(), "worker-retry", 10, (destination, event) -> {}));
        assertEquals("PUBLISHED", status(ref, "status"));
        assertEquals(2, statusInt(ref, "attempt_count"));
    }

    @Test
    void remoteAcceptanceBeforeLostAcknowledgementProducesSafeAtLeastOnceRedelivery() throws Exception {
        EventRef ref = insertEvent("at-least-once");
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<UUID> stableId = new AtomicReference<>();
        JdbcCourierOutboxPublisher relay = new JdbcCourierOutboxPublisher(dataSource);

        int first = relay.publishBatch(ref.destination(), "worker-crash-window", 10, (destination, event) -> {
            stableId.set(event.eventId());
            calls.incrementAndGet();
            // Models the destination accepting the message while the relay observes an ambiguous failure
            // before it can commit its local PUBLISHED acknowledgement.
            throw new IllegalStateException("simulated acknowledgement loss after remote acceptance");
        });
        assertEquals(0, first);
        assertEquals("PENDING", status(ref, "status"));

        execute("UPDATE courier_event_publications SET available_at = now() WHERE tenant_id = ? AND event_id = ? AND destination = ?",
                ref);
        assertEquals(1, relay.publishBatch(ref.destination(), "worker-recovered", 10, (destination, event) -> {
            calls.incrementAndGet();
            assertEquals(stableId.get(), event.eventId(), "redelivery must retain the idempotency key");
        }));
        assertEquals(2, calls.get());
        assertEquals("PUBLISHED", status(ref, "status"));
    }

    @Test
    void expiredWorkerLeaseCanBeRecoveredByAnotherWorker() throws Exception {
        EventRef ref = insertEvent("lease-recovery");
        execute("""
                UPDATE courier_event_publications
                SET lease_owner = 'dead-worker', lease_until = now() - interval '5 seconds',
                    attempt_count = 1
                WHERE tenant_id = ? AND event_id = ? AND destination = ?
                """, ref);

        assertEquals(1, new JdbcCourierOutboxPublisher(dataSource).publishBatch(
                ref.destination(), "recovery-worker", 10, (destination, event) -> {}));
        assertEquals("PUBLISHED", status(ref, "status"));
        assertEquals(2, statusInt(ref, "attempt_count"));
    }

    @Test
    void authorizedDeadLetterReplayResetsDeliveryAndWritesImmutableAudit() throws Exception {
        EventRef ref = insertEvent("ops-replay");
        execute("""
                UPDATE courier_event_publications
                SET status = 'DEAD', attempt_count = 12, last_error = 'terminal failure'
                WHERE tenant_id = ? AND event_id = ? AND destination = ?
                """, ref);

        CourierOutboxOperationsAuthorizer allowOps = new CourierOutboxOperationsAuthorizer() {
            @Override public boolean mayInspect(String actor, String destination) { return true; }
            @Override public boolean mayReplay(String actor, String destination, String tenantId, String eventId) {
                return "ops-user".equals(actor) && ref.tenantId().toString().equals(tenantId)
                        && ref.eventId().toString().equals(eventId);
            }
        };
        CourierOutboxOperations operations = new CourierOutboxOperations(dataSource, allowOps);
        CourierOutboxOperations.ReplayResult result = operations.replayDeadLetter(
                "ops-user", "verified destination recovery", ref.tenantId(), ref.eventId(), ref.destination());

        assertEquals("PENDING", result.status());
        assertEquals(12, result.previousAttemptCount());
        assertEquals("PENDING", status(ref, "status"));
        assertEquals(0, statusInt(ref, "attempt_count"));
        assertEquals(1, statusInt(ref, "replay_count"));
        assertEquals("ops-user", status(ref, "last_replayed_by"));
        assertEquals(1, auditCount(ref));

        assertThrows(SecurityException.class, () -> operations.replayDeadLetter(
                "intruder", "unauthorized attempt", ref.tenantId(), ref.eventId(), ref.destination()));
    }

    private static EventRef insertEvent(String suffix) throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String destination = "m28-" + suffix;
        try (Connection c = dataSource.getConnection(); PreparedStatement event = c.prepareStatement("""
                INSERT INTO courier_event_outbox
                  (tenant_id, id, event_type, aggregate_type, aggregate_reference, occurred_at, payload)
                VALUES (?, ?, 'courier.test.v1', 'shipment', ?, now(), CAST(? AS jsonb))
                """)) {
            event.setObject(1, tenant);
            event.setObject(2, eventId);
            event.setString(3, "SHIP-" + eventId);
            event.setString(4, "{\"eventId\":\"" + eventId + "\"}");
            assertEquals(1, event.executeUpdate());
        }
        try (Connection c = dataSource.getConnection(); PreparedStatement publication = c.prepareStatement("""
                INSERT INTO courier_event_publications (tenant_id, event_id, destination)
                VALUES (?, ?, ?)
                """)) {
            publication.setObject(1, tenant);
            publication.setObject(2, eventId);
            publication.setString(3, destination);
            assertEquals(1, publication.executeUpdate());
        }
        return new EventRef(tenant, eventId, destination);
    }

    private static String status(EventRef ref, String column) throws Exception {
        if (!column.matches("[a-z_]+")) throw new IllegalArgumentException("invalid column");
        try (Connection c = dataSource.getConnection(); PreparedStatement query = c.prepareStatement(
                "SELECT " + column + " FROM courier_event_publications WHERE tenant_id = ? AND event_id = ? AND destination = ?")) {
            bind(query, ref);
            try (ResultSet rs = query.executeQuery()) {
                assertTrue(rs.next());
                Object value = rs.getObject(1);
                return value == null ? null : value.toString();
            }
        }
    }

    private static int statusInt(EventRef ref, String column) throws Exception {
        return Integer.parseInt(status(ref, column));
    }

    private static int auditCount(EventRef ref) throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement query = c.prepareStatement("""
                SELECT count(*) FROM courier_event_publication_audit
                WHERE tenant_id = ? AND event_id = ? AND destination = ?
                """)) {
            bind(query, ref);
            try (ResultSet rs = query.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static void execute(String sql, EventRef ref) throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement update = c.prepareStatement(sql)) {
            bind(update, ref);
            assertEquals(1, update.executeUpdate());
        }
    }

    private static void bind(PreparedStatement statement, EventRef ref) throws Exception {
        statement.setObject(1, ref.tenantId());
        statement.setObject(2, ref.eventId());
        statement.setString(3, ref.destination());
    }

    private record EventRef(UUID tenantId, UUID eventId, String destination) {}
}
