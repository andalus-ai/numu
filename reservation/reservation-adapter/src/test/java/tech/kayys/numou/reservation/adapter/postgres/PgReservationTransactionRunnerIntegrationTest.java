package tech.kayys.numou.reservation.adapter.postgres;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.reservation.domain.*;
import tech.kayys.numou.reservation.domain.event.ReservationRequested;
import tech.kayys.numou.reservation.spi.IdempotentReservationRepository;
import tech.kayys.numou.reservation.spi.ReservationTransactionContext;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Opt-in PostgreSQL contract test. Applies the production migration and verifies that a
 * failure after all four writes causes PostgreSQL to roll back the complete transaction.
 */
@QuarkusTest
class PgReservationTransactionRunnerIntegrationTest {
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
    void failureAfterReservationLedgerIdempotencyAndOutboxWritesRollsBackEverything() {
        UUID tenantId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String idempotencyKey = "rollback-" + UUID.randomUUID();
        String fingerprint = "a".repeat(64);
        Instant now = Instant.parse("2026-10-28T04:00:00Z");
        OffsetDateTime startsAt = now.atOffset(ZoneOffset.UTC);
        OffsetDateTime endsAt = now.plusSeconds(3600).atOffset(ZoneOffset.UTC);

        PgReservationTransactionRunner runner = new PgReservationTransactionRunner(pool);
        assertThrows(RuntimeException.class, () -> runner.inTransaction(connection ->
                insertReservation(connection, tenantId, reservationId, facilityId, startsAt, endsAt, now)
                        .chain(() -> insertLedger(connection, tenantId, ledgerId, facilityId, now))
                        .chain(() -> insertIdempotency(connection, tenantId, idempotencyKey,
                                fingerprint, reservationId, now))
                        .chain(() -> new PgReservationOutboxEventSink(connection).publish(tenantId,
                                java.util.List.of(new ReservationRequested(eventId, now, reservationId,
                                        facilityId, 2, now.plusSeconds(600), "PENDING"))))
                        .chain(() -> Uni.createFrom().<Void>failure(
                                new DeliberateRollbackException())))
                .await().indefinitely());

        assertEquals(0L, count("reservation_record", tenantId, "reservation_id", reservationId));
        assertEquals(0L, count("reservation_availability_ledger", tenantId, "ledger_id", ledgerId));
        assertEquals(0L, countIdempotency(tenantId, idempotencyKey));
        assertEquals(0L, countOutbox(eventId));
    }

    @Test
    void concurrentSameKeyAndFingerprintConvergeOnOneCanonicalReservation() {
        UUID tenantId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();
        String key = "same-request-" + UUID.randomUUID();
        String fingerprint = "b".repeat(64);
        Instant now = Instant.parse("2026-10-28T04:00:00Z");
        Reservation firstCandidate = candidate(facilityId, now);
        Reservation secondCandidate = candidate(facilityId, now);
        PgReservationTransactionRunner runner = transactionRunner();

        var results = Uni.combine().all().unis(
                saveIdempotently(runner, tenantId, key, fingerprint, firstCandidate),
                saveIdempotently(runner, tenantId, key, fingerprint, secondCandidate)
        ).asTuple().await().indefinitely();

        assertEquals(1, (results.getItem1().created() ? 1 : 0)
                + (results.getItem2().created() ? 1 : 0));
        assertEquals(results.getItem1().reservation().id(), results.getItem2().reservation().id());
        assertEquals(1L, countIdempotency(tenantId, key));
        assertEquals(1L, countReservationId(tenantId, results.getItem1().reservation().id().value()));
    }

    @Test
    void concurrentSameKeyWithDifferentFingerprintsAllowsOnlyOneRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();
        String key = "conflicting-request-" + UUID.randomUUID();
        Instant now = Instant.parse("2026-10-28T04:00:00Z");
        PgReservationTransactionRunner runner = transactionRunner();

        Uni<Boolean> first = saveIdempotently(runner, tenantId, key, "c".repeat(64),
                candidate(facilityId, now))
                .map(result -> true)
                .onFailure(IdempotentReservationRepository.IdempotencyKeyConflictException.class)
                .recoverWithItem(false);
        Uni<Boolean> second = saveIdempotently(runner, tenantId, key, "d".repeat(64),
                candidate(facilityId, now))
                .map(result -> true)
                .onFailure(IdempotentReservationRepository.IdempotencyKeyConflictException.class)
                .recoverWithItem(false);

        var results = Uni.combine().all().unis(first, second).asTuple().await().indefinitely();
        assertNotEquals(results.getItem1(), results.getItem2());
        assertEquals(1L, countIdempotency(tenantId, key));
        assertEquals(1L, countReservationsForTenant(tenantId));
    }

    private PgReservationTransactionRunner transactionRunner() {
        return new PgReservationTransactionRunner(pool,
                connection -> (tenantId, events) -> Uni.createFrom().voidItem());
    }

    private Uni<IdempotentReservationRepository.IdempotentSaveResult> saveIdempotently(
            PgReservationTransactionRunner runner, UUID tenantId, String key, String fingerprint,
            Reservation candidate) {
        return runner.execute(context -> context.reservations()
                .saveIdempotently(tenantId, key, fingerprint, candidate));
    }

    private Reservation candidate(UUID facilityId, Instant now) {
        ZoneId zone = ZoneId.of("Asia/Jakarta");
        BookingRules rules = new BookingRules(
                zone, 1, 10, 30,
                Duration.ofMinutes(30), Duration.ofMinutes(60), Duration.ofMinutes(15),
                java.util.Map.of(2, Duration.ofMinutes(60)),
                30, Money.of(100_000, "IDR"),
                Duration.ofHours(24), Money.of(50_000, "IDR"),
                Duration.ofMinutes(15), Duration.ofMinutes(15),
                java.util.Map.of(DayOfWeek.FRIDAY, java.util.List.of(
                        new OperatingWindow("Day", LocalTime.of(8, 0), LocalTime.of(22, 0),
                                LocalTime.of(20, 0))))
        );
        Instant startsAt = java.time.LocalDateTime.of(2026, 10, 30, 14, 0)
                .atZone(zone).toInstant();
        return Reservation.request(ReservationId.generate(), FacilityId.of(facilityId),
                new CustomerContact("Integration Guest", "+6281112233", "integration@example.com"),
                PartySize.of(2), startsAt, BookingSource.WEB, "concurrency test", java.util.Set.of(),
                Duration.ofHours(1), rules, now);
    }

    private long countReservationId(UUID tenantId, UUID reservationId) {
        return pool.preparedQuery("""
                SELECT count(*) AS total FROM reservation_record
                 WHERE tenant_id = $1 AND reservation_id = $2
                """).execute(Tuple.of(tenantId, reservationId)).await().indefinitely()
                .iterator().next().getLong("total");
    }

    private long countReservationsForTenant(UUID tenantId) {
        return pool.preparedQuery("""
                SELECT count(*) AS total FROM reservation_record WHERE tenant_id = $1
                """).execute(Tuple.of(tenantId)).await().indefinitely()
                .iterator().next().getLong("total");
    }

    private Uni<Void> insertReservation(SqlConnection connection, UUID tenantId, UUID reservationId,
                                        UUID facilityId, OffsetDateTime startsAt, OffsetDateTime endsAt,
                                        Instant now) {
        return connection.preparedQuery("""
                INSERT INTO reservation_record
                    (tenant_id, reservation_id, facility_id, starts_at, ends_at, status,
                     aggregate_version, snapshot, created_at, updated_at)
                VALUES ($1, $2, $3, $4, $5, 'PENDING', 1, '{}'::jsonb, $6, $6)
                """).execute(Tuple.of(tenantId, reservationId, facilityId, startsAt, endsAt,
                now.atOffset(ZoneOffset.UTC))).replaceWithVoid();
    }

    private Uni<Void> insertLedger(SqlConnection connection, UUID tenantId, UUID ledgerId,
                                   UUID facilityId, Instant now) {
        return connection.preparedQuery("""
                INSERT INTO reservation_availability_ledger
                    (tenant_id, ledger_id, facility_id, ledger_day, aggregate_version, snapshot,
                     created_at, updated_at)
                VALUES ($1, $2, $3, $4, 1, '{}'::jsonb, $5, $5)
                """).execute(Tuple.of(tenantId, ledgerId, facilityId, LocalDate.of(2026, 10, 28),
                now.atOffset(ZoneOffset.UTC))).replaceWithVoid();
    }

    private Uni<Void> insertIdempotency(SqlConnection connection, UUID tenantId, String key,
                                        String fingerprint, UUID reservationId, Instant now) {
        return connection.preparedQuery("""
                INSERT INTO reservation_idempotency_key
                    (tenant_id, idempotency_key, request_fingerprint, reservation_id, created_at)
                VALUES ($1, $2, $3, $4, $5)
                """).execute(Tuple.of(tenantId, key, fingerprint, reservationId,
                now.atOffset(ZoneOffset.UTC))).replaceWithVoid();
    }

    private long count(String table, UUID tenantId, String idColumn, UUID id) {
        // Table and column names are constants at call sites, never user input.
        return pool.preparedQuery("SELECT count(*) AS total FROM " + table
                + " WHERE tenant_id = $1 AND " + idColumn + " = $2")
                .execute(Tuple.of(tenantId, id)).await().indefinitely()
                .iterator().next().getLong("total");
    }

    private long countIdempotency(UUID tenantId, String key) {
        return pool.preparedQuery("""
                SELECT count(*) AS total FROM reservation_idempotency_key
                 WHERE tenant_id = $1 AND idempotency_key = $2
                """).execute(Tuple.of(tenantId, key)).await().indefinitely()
                .iterator().next().getLong("total");
    }

    private long countOutbox(UUID eventId) {
        return pool.preparedQuery("""
                SELECT count(*) AS total FROM reservation_outbox_event WHERE event_id = $1
                """).execute(Tuple.of(eventId)).await().indefinitely()
                .iterator().next().getInteger("total");
    }

    private static final class DeliberateRollbackException extends RuntimeException { }
}
