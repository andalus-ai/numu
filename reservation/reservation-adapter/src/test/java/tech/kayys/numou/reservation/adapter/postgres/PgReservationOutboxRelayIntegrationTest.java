package tech.kayys.numou.reservation.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in PostgreSQL contract tests for claim leases, retries, and dead-letter transitions. */
@QuarkusTest
class PgReservationOutboxRelayIntegrationTest {
    @Inject
    PgPool pool;

    @BeforeEach
    void applyProductionMigration() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V1__reservation_transactional_persistence.sql")) {
            if (input == null) throw new IllegalStateException("Reservation production migration not found");
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            for (String statement : migration.split(";")) {
                if (!statement.isBlank()) pool.query(statement).execute().await().indefinitely();
            }
        }
    }

    @Test
    void publishesClaimedEventsAndAcknowledgesThem() {
        UUID tenantId = UUID.randomUUID();
        UUID first = insertEvent(tenantId, "success");
        UUID second = insertEvent(tenantId, "success");
        List<UUID> delivered = new CopyOnWriteArrayList<>();
        PgReservationOutboxRelay relay = relay(message -> {
            delivered.add(message.eventId());
            return Uni.createFrom().voidItem();
        }, 10, 3);

        assertEquals(2, relay.pollOnce().await().indefinitely());
        assertEquals(List.of(first, second), delivered);
        assertEquals(2L, countStatus(tenantId, "PUBLISHED"));
        assertEquals(0L, countStatus(tenantId, "PROCESSING"));
    }

    @Test
    void failedDeliveryMovesEventToDeadLetterAfterAttemptLimit() {
        UUID tenantId = UUID.randomUUID();
        insertEvent(tenantId, "failure");
        PgReservationOutboxRelay relay = relay(message ->
                Uni.createFrom().failure(new IllegalStateException("broker unavailable")), 10, 1);

        assertEquals(0, relay.pollOnce().await().indefinitely());
        assertEquals(1L, countStatus(tenantId, "DEAD"));
        assertEquals(1L, countAttempts(tenantId, 1));
    }

    @Test
    void retryIsDelayedAndExpiredProcessingLeaseCanBeReclaimed() {
        UUID tenantId = UUID.randomUUID();
        UUID eventId = insertEvent(tenantId, "retry");
        PgReservationOutboxRelay failing = relay(message ->
                Uni.createFrom().failure(new IllegalStateException("temporary outage")), 10, 3);

        assertEquals(0, failing.pollOnce().await().indefinitely());
        assertEquals(1L, countStatus(tenantId, "PENDING"));
        assertEquals(1L, countAttempts(tenantId, 1));

        pool.preparedQuery("""
                UPDATE reservation_outbox_event
                   SET available_at = clock_timestamp() - INTERVAL '1 second'
                 WHERE event_id = $1
                """).execute(Tuple.of(eventId)).await().indefinitely();
        pool.preparedQuery("""
                UPDATE reservation_outbox_event
                   SET status = 'PROCESSING', lease_owner = 'crashed-worker',
                       lease_until = clock_timestamp() - INTERVAL '1 second'
                 WHERE event_id = $1
                """).execute(Tuple.of(eventId)).await().indefinitely();

        PgReservationOutboxRelay succeeding = relay(message -> Uni.createFrom().voidItem(), 10, 3);
        assertEquals(1, succeeding.pollOnce().await().indefinitely());
        assertEquals(1L, countStatus(tenantId, "PUBLISHED"));
        assertEquals(2L, countAttempts(tenantId, 2));
    }

    @Test
    void concurrentPollersClaimDifferentRowsWithoutDoubleClaiming() {
        UUID tenantId = UUID.randomUUID();
        insertEvent(tenantId, "parallel");
        insertEvent(tenantId, "parallel");
        List<UUID> delivered = new CopyOnWriteArrayList<>();
        ReservationOutboxPublisher publisher = message -> {
            delivered.add(message.eventId());
            return Uni.createFrom().voidItem();
        };
        PgReservationOutboxRelay first = relay(publisher, 1, 3);
        PgReservationOutboxRelay second = relay(publisher, 1, 3);

        var results = Uni.combine().all().unis(first.pollOnce(), second.pollOnce())
                .asTuple().await().indefinitely();

        assertEquals(1, results.getItem1());
        assertEquals(1, results.getItem2());
        assertEquals(2, delivered.size());
        assertEquals(2, delivered.stream().distinct().count());
        assertEquals(2L, countStatus(tenantId, "PUBLISHED"));
    }

    private PgReservationOutboxRelay relay(ReservationOutboxPublisher publisher, int batchSize, int maxAttempts) {
        return new PgReservationOutboxRelay(pool, publisher, batchSize, maxAttempts,
                Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofSeconds(30));
    }

    private UUID insertEvent(UUID tenantId, String marker) {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        pool.preparedQuery("""
                INSERT INTO reservation_outbox_event
                    (event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload,
                     occurred_at, available_at)
                VALUES ($1, $2, 'Reservation', $3, 'reservation.test', $4::jsonb,
                        clock_timestamp(), clock_timestamp())
                """).execute(Tuple.of(eventId, tenantId, aggregateId,
                "{\"marker\":\"" + marker + "\"}")).await().indefinitely();
        return eventId;
    }

    private long countStatus(UUID tenantId, String status) {
        return pool.preparedQuery("""
                SELECT count(*) AS total FROM reservation_outbox_event
                 WHERE tenant_id = $1 AND status = $2
                """).execute(Tuple.of(tenantId, status)).await().indefinitely()
                .iterator().next().getLong("total");
    }

    private long countAttempts(UUID tenantId, int attempts) {
        return pool.preparedQuery("""
                SELECT count(*) AS total FROM reservation_outbox_event
                 WHERE tenant_id = $1 AND attempts = $2
                """).execute(Tuple.of(tenantId, attempts)).await().indefinitely()
                .iterator().next().getLong("total");
    }
}
