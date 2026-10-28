package tech.kayys.numou.reservation.application;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.reservation.adapter.memory.InMemoryReservationRepositories;
import tech.kayys.numou.reservation.adapter.postgres.PgReservationOutboxEventSink;
import tech.kayys.numou.reservation.adapter.postgres.PgReservationTransactionRunner;
import tech.kayys.numou.reservation.domain.*;
import tech.kayys.numou.reservation.spi.BookingRulesPort;
import tech.kayys.numou.reservation.spi.DomainEventSink;
import tech.kayys.numou.reservation.spi.LedgerRepository;
import tech.kayys.numou.reservation.spi.ReservationRepository;
import tech.kayys.numou.reservation.spi.ResourceDirectoryPort;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Opt-in application-level contract test. Unlike the adapter tests, this drives the real
 * ReservationService booking path with PostgreSQL transaction-scoped repositories and outbox.
 */
@QuarkusTest
class ReservationServicePostgresIntegrationTest {
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
    void serviceCreateAndIdempotentReplayPersistOneReservationAllocationAndOutboxEvent() {
        UUID tenantId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-28T04:00:00Z");
        Instant startsAt = LocalDateTime.of(2026, 10, 30, 14, 0)
                .atZone(ZoneId.of("Asia/Jakarta")).toInstant();
        BookableResource resource = new BookableResource(
                ResourceId.generate(), "Integration resource", 1, 2, Set.of(), Set.of());

        BookingRules rules = new BookingRules(
                ZoneId.of("Asia/Jakarta"), 1, 10, 30,
                Duration.ofMinutes(30), Duration.ofMinutes(60), Duration.ofMinutes(15),
                Map.of(2, Duration.ofMinutes(60)),
                30, Money.of(100_000, "IDR"),
                Duration.ofHours(24), Money.of(50_000, "IDR"),
                Duration.ofMinutes(15), Duration.ofMinutes(15),
                Map.of(DayOfWeek.FRIDAY, List.of(
                        new OperatingWindow("Day", LocalTime.of(8, 0), LocalTime.of(22, 0),
                                LocalTime.of(20, 0)))));
        BookingRulesPort rulesPort = (tenant, facility) -> Uni.createFrom().item(rules);
        ResourceDirectoryPort directory = (tenant, facility) -> Uni.createFrom().item(List.of(resource));
        DomainClock clock = () -> now;
        UnitOfWork legacyUnitOfWork = new UnitOfWork() {
            @Override
            public <R> Uni<R> execute(Supplier<Uni<R>> work) {
                return work.get();
            }
        };
        DomainEventSink legacyEvents = (tenant, events) -> Uni.createFrom().voidItem();
        PgReservationTransactionRunner runner = new PgReservationTransactionRunner(
                pool, connection -> new PgReservationOutboxEventSink(connection));
        ReservationRepository legacyReservations = new InMemoryReservationRepositories.Reservations();
        LedgerRepository legacyLedgers = new InMemoryReservationRepositories.Ledgers();
        ReservationService service = new ReservationService(
                legacyReservations, legacyLedgers, directory, rulesPort,
                legacyUnitOfWork, legacyEvents, clock, runner);

        CustomerContact customer = new CustomerContact(
                "PostgreSQL Integration Guest", "+6281112233", "reservation-integration@example.com");
        String key = "service-integration-" + UUID.randomUUID();

        ReservationView created = service.bookWithConstraints(
                tenantId, facilityId, customer, 2, startsAt, Duration.ofMinutes(60),
                Set.of(), BookingSource.WEB, "integration transaction", Set.of(), key)
                .await().indefinitely();
        long outboxAfterCreate = countOutbox(tenantId);

        ReservationView replay = service.bookWithConstraints(
                tenantId, facilityId, customer, 2, startsAt, Duration.ofMinutes(60),
                Set.of(), BookingSource.WEB, "integration transaction", Set.of(), key)
                .await().indefinitely();

        assertEquals(created.id(), replay.id(), "replay returns the canonical reservation");
        assertEquals(1L, count("reservation_record", tenantId));
        assertEquals(1L, countIdempotency(tenantId, key));
        assertEquals(1L, countLedgerAllocations(tenantId, facilityId,
                startsAt.atZone(rules.zone()).toLocalDate()));
        assertEquals(outboxAfterCreate, countOutbox(tenantId),
                "idempotent replay must not write duplicate outbox events");
        assertEquals(1L, outboxAfterCreate, "new booking should persist its domain event to the outbox");
    }

    private long count(String table, UUID tenantId) {
        // The table name is a constant at the call site, never user input.
        return pool.preparedQuery("SELECT count(*) AS total FROM " + table + " WHERE tenant_id = $1")
                .execute(Tuple.of(tenantId)).await().indefinitely()
                .iterator().next().getLong("total");
    }

    private long countIdempotency(UUID tenantId, String key) {
        return pool.preparedQuery("""
                SELECT count(*) AS total FROM reservation_idempotency_key
                 WHERE tenant_id = $1 AND idempotency_key = $2
                """).execute(Tuple.of(tenantId, key)).await().indefinitely()
                .iterator().next().getLong("total");
    }

    private long countOutbox(UUID tenantId) {
        return pool.preparedQuery("""
                SELECT count(*) AS total FROM reservation_outbox_event WHERE tenant_id = $1
                """).execute(Tuple.of(tenantId)).await().indefinitely()
                .iterator().next().getLong("total");
    }

    private long countLedgerAllocations(UUID tenantId, UUID facilityId, LocalDate day) {
        return pool.preparedQuery("""
                SELECT COALESCE(sum(jsonb_array_length(snapshot->'allocations')), 0) AS total
                  FROM reservation_availability_ledger
                 WHERE tenant_id = $1 AND facility_id = $2 AND ledger_day = $3
                """).execute(Tuple.of(tenantId, facilityId, day)).await().indefinitely()
                .iterator().next().getLong("total");
    }
}
