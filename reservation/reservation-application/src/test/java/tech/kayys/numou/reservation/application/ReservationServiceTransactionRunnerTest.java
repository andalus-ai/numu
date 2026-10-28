package tech.kayys.numou.reservation.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.reservation.adapter.memory.InMemoryReservationRepositories;
import tech.kayys.numou.reservation.domain.*;
import tech.kayys.numou.reservation.spi.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that configuring the transaction runner changes the persistence collaborators
 * used by constrained/idempotent booking, rather than merely wrapping legacy repositories.
 */
class ReservationServiceTransactionRunnerTest {

    @Test
    void constrainedIdempotentBookingUsesTransactionScopedCollaboratorsForCreateAndReplay() {
        UUID tenant = UUID.randomUUID();
        UUID facility = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-09T06:00:00Z");
        Instant startsAt = Instant.parse("2026-10-09T07:00:00Z");

        var legacyReservations = new InMemoryReservationRepositories.Reservations();
        var legacyLedgers = new InMemoryReservationRepositories.Ledgers();
        var transactionReservations = new InMemoryReservationRepositories.Reservations();
        var transactionLedgers = new InMemoryReservationRepositories.Ledgers();

        AtomicInteger transactionCalls = new AtomicInteger();
        AtomicInteger transactionEvents = new AtomicInteger();
        AtomicInteger legacyEvents = new AtomicInteger();

        DomainEventSink transactionSink = (t, events) -> {
            transactionEvents.addAndGet(events.size());
            return Uni.createFrom().voidItem();
        };
        DomainEventSink legacySink = (t, events) -> {
            legacyEvents.addAndGet(events.size());
            return Uni.createFrom().voidItem();
        };

        ReservationTransactionRunner runner = new ReservationTransactionRunner() {
            @Override
            public <T> Uni<T> execute(java.util.function.Function<ReservationTransactionContext, Uni<T>> work) {
                transactionCalls.incrementAndGet();
                return work.apply(new ReservationTransactionContext(
                        transactionReservations, transactionLedgers, transactionSink));
            }
        };

        var room = ResourceId.generate();
        ResourceDirectoryPort directory = (t, f) -> Uni.createFrom().item(List.of(
                new BookableResource(room, "Room 101", 1, 2, Set.of())
        ));
        var schedule = Map.of(DayOfWeek.FRIDAY, List.of(
                new OperatingWindow("Day", LocalTime.of(8, 0), LocalTime.of(22, 0), LocalTime.of(20, 0))));
        var rules = new BookingRules(
                ZoneId.of("Asia/Jakarta"), 1, 10, 30,
                Duration.ofMinutes(30), Duration.ofMinutes(60), Duration.ofMinutes(15),
                Map.of(2, Duration.ofMinutes(60)), 4, Money.of(100_000, "IDR"),
                Duration.ofHours(24), Money.of(50_000, "IDR"),
                Duration.ofMinutes(15), Duration.ofMinutes(15), schedule);
        BookingRulesPort rulesPort = (t, f) -> Uni.createFrom().item(rules);
        UnitOfWork uow = new UnitOfWork() {
            @Override
            public <R> Uni<R> execute(Supplier<Uni<R>> work) {
                return work.get();
            }
        };
        DomainClock clock = () -> now;

        var service = new ReservationService(
                legacyReservations, legacyLedgers, directory, rulesPort, uow, legacySink, clock, runner);
        var customer = new CustomerContact("Workshop Customer", "+6281112233", "customer@example.com");

        var created = service.bookWithConstraints(tenant, facility, customer, 1, startsAt,
                Duration.ofMinutes(60), Set.of(), BookingSource.WEB, "oil change",
                Set.of("vehicle-service"), "transaction-runner-key").await().indefinitely();
        int eventCountAfterCreate = transactionEvents.get();

        var replay = service.bookWithConstraints(tenant, facility, customer, 1, startsAt,
                Duration.ofMinutes(60), Set.of(), BookingSource.WEB, "oil change",
                Set.of("vehicle-service"), "transaction-runner-key").await().indefinitely();

        assertThat(transactionCalls).hasValue(2);
        assertThat(created.id()).isEqualTo(replay.id());
        assertThat(transactionReservations.allOf(tenant)).hasSize(1);
        assertThat(legacyReservations.allOf(tenant)).isEmpty();
        assertThat(transactionEvents.get()).isPositive().isEqualTo(eventCountAfterCreate);
        assertThat(legacyEvents).hasValue(0);
    }
}
