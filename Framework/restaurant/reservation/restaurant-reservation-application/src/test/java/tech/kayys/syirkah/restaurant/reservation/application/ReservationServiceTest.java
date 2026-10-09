package tech.kayys.syirkah.restaurant.reservation.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.restaurant.kernel.domain.*;
import tech.kayys.syirkah.restaurant.kernel.memory.RecordingEventSink;
import tech.kayys.syirkah.restaurant.reservation.adapter.memory.*;
import tech.kayys.syirkah.restaurant.reservation.domain.*;
import tech.kayys.syirkah.restaurant.reservation.spi.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationServiceTest {

    static final ZoneId JKT = ZoneId.of("Asia/Jakarta");
    static final LocalDate SAT = LocalDate.of(2026, 10, 10);
    static Instant at(int h, int m) { return SAT.atTime(h, m).atZone(JKT).toInstant(); }

    final UUID tenant = UUID.randomUUID();
    final VenueId venue = VenueId.generate();
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-07T03:00:00Z"));
    final UnitOfWork uow = new UnitOfWork() { public <R> Uni<R> execute(java.util.function.Supplier<Uni<R>> w) { return w.get(); } };
    final RecordingEventSink sink = new RecordingEventSink();
    InMemoryReservationRepository reservations;
    LedgerRepository ledgers;
    List<TableInfo> tables;
    ReservationService service;

    static BookingRules rules() {
        final var periods = new EnumMap<DayOfWeek, List<ServicePeriod>>(DayOfWeek.class);
        for (var d : DayOfWeek.values()) {
            periods.put(d, List.of(new ServicePeriod("lunch", LocalTime.of(11, 30), LocalTime.of(14, 0)),
                    new ServicePeriod("dinner", LocalTime.of(17, 0), LocalTime.of(21, 0))));
        }
        return new BookingRules(JKT, 1, 12, 60, Duration.ofMinutes(60), Duration.ofMinutes(30), Duration.ofMinutes(15),
                Map.of(2, Duration.ofMinutes(75), 4, Duration.ofMinutes(90), 6, Duration.ofMinutes(105), 99, Duration.ofMinutes(120)),
                8, Money.of(100_000, "IDR"), Duration.ofHours(24), Money.of(50_000, "IDR"),
                Duration.ofMinutes(15), Duration.ofMinutes(15), periods);
    }

    @BeforeEach
    void setUp() {
        reservations = new InMemoryReservationRepository();
        ledgers = new InMemoryLedgerRepository();
        tables = List.of(new TableInfo(DiningTableId.generate(), "T1", 1, 4, Set.of()));
        service = build(ledgers);
    }

    ReservationService build(LedgerRepository ledgerRepo) {
        final BookingRulesPort rulesPort = (t, v) -> Uni.createFrom().item(rules());
        final TableDirectoryPort dir = (t, v) -> Uni.createFrom().item(tables);
        return new ReservationService(reservations, ledgerRepo, rulesPort, dir, uow, sink, now::get);
    }

    ReservationService.CreateCommand cmd(UUID tenantId, int party, Instant start) {
        return new ReservationService.CreateCommand(tenantId, venue.value(), "Budi Santoso", "+62 812 3456 7890", null,
                party, start, BookingSource.WEB, "", Set.of());
    }

    // ---------------------------------------------------------------- create

    @Test
    void createBooksATableAndPublishesEventsInOneGo() {
        final var view = service.create(cmd(tenant, 2, at(19, 0))).await().indefinitely();
        assertThat(view.status()).isEqualTo("CONFIRMED");
        assertThat(view.tableIds()).containsExactly(tables.get(0).id().value());
        assertThat(sink.types()).containsExactlyInAnyOrder("restaurant.reservation.requested",
                "restaurant.reservation.confirmed", "restaurant.reservation.tables-assigned");
        assertThat(sink.all()).allMatch(p -> p.tenantId().equals(tenant));
    }

    @Test
    void secondBookingOfTheOnlyTableIsRefusedWithNearbyAlternatives() {
        service.create(cmd(tenant, 2, at(19, 0))).await().indefinitely(); // table blocked 19:00-20:30
        assertThatThrownBy(() -> service.create(cmd(tenant, 2, at(19, 30))).await().indefinitely())
                .isInstanceOfSatisfying(SlotUnavailableException.class, e -> {
                    assertThat(e.alternatives()).hasSize(3).isSorted();
                    assertThat(e.alternatives()).doesNotContain(at(19, 0), at(19, 30), at(20, 0));
                    assertThat(e.alternatives()).contains(at(20, 30)); // earliest free start after the first party leaves
                });
        assertThat(reservations.findStartingBetween(tenant, venue, at(0, 0), at(23, 59)).await().indefinitely()).hasSize(1);
    }

    @Test
    void ruleViolationsAreNotRetriedAndLeaveNoTrace() {
        assertThatThrownBy(() -> service.create(cmd(tenant, 2, at(15, 0))).await().indefinitely())
                .isInstanceOf(BusinessRuleViolation.class);
        assertThat(sink.all()).isEmpty();
    }

    // ---------------------------------------------------------------- the race for the last table

    /** Ledger repo that lets another writer sneak a booking in between our load and our save, exactly once. */
    static final class RacingLedgerRepository extends InMemoryLedgerRepository {
        Runnable beforeFirstSave;
        final AtomicInteger saves = new AtomicInteger();
        @Override public Uni<Void> save(UUID tenantId, AvailabilityLedger l) {
            if (saves.incrementAndGet() == 1 && beforeFirstSave != null) { final var r = beforeFirstSave; beforeFirstSave = null; r.run(); }
            return super.save(tenantId, l);
        }
    }

    @Test
    void loserOfTheRaceReloadsSeesTheTableTakenAndIsRefusedNeverDoubleBooked() {
        final var racing = new RacingLedgerRepository();
        final var svc = build(racing);
        final var other = build(racing);
        racing.beforeFirstSave = () -> other.create(cmd(tenant, 2, at(19, 0))).await().indefinitely(); // wins the race

        assertThatThrownBy(() -> svc.create(cmd(tenant, 2, at(19, 0))).await().indefinitely())
                .isInstanceOf(SlotUnavailableException.class);
        assertThat(reservations.findStartingBetween(tenant, venue, at(0, 0), at(23, 59)).await().indefinitely()).hasSize(1);
    }

    @Test
    void loserOfTheRaceStillGetsATableWhenAnotherIsFree() {
        tables = List.of(new TableInfo(DiningTableId.generate(), "T1", 1, 4, Set.of()),
                new TableInfo(DiningTableId.generate(), "T2", 1, 4, Set.of()));
        final var racing = new RacingLedgerRepository();
        final var svc = build(racing);
        final var other = build(racing);
        racing.beforeFirstSave = () -> other.create(cmd(tenant, 2, at(19, 0))).await().indefinitely();

        final var view = svc.create(cmd(tenant, 2, at(19, 0))).await().indefinitely(); // retried transparently
        assertThat(view.status()).isEqualTo("CONFIRMED");
        final var all = reservations.findStartingBetween(tenant, venue, at(0, 0), at(23, 59)).await().indefinitely();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).tableIds()).isNotEqualTo(all.get(1).tableIds());
    }

    @Test
    void manyThreadsFightingForOneTableProduceExactlyOneBooking() throws Exception {
        for (int round = 0; round < 15; round++) {
            setUp();
            final int threads = 8;
            final var pool = Executors.newFixedThreadPool(threads);
            final var start = new CountDownLatch(1);
            final List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try { service.create(cmd(tenant, 2, at(19, 0))).await().indefinitely(); return true; }
                    catch (SlotUnavailableException e) { return false; }
                }));
            }
            start.countDown();
            int ok = 0;
            for (var f : results) if (f.get(10, TimeUnit.SECONDS)) ok++;
            pool.shutdown();
            assertThat(ok).as("round %d: successful bookings", round).isEqualTo(1);
            assertThat(reservations.findStartingBetween(tenant, venue, at(0, 0), at(23, 59)).await().indefinitely()).hasSize(1);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    void cancellingWellInAdvanceIsFreeAndReleasesTheSlot() {
        final var first = service.create(cmd(tenant, 2, at(19, 0))).await().indefinitely();
        final var result = service.cancel(tenant, first.id(), "changed plans", false).await().indefinitely();
        assertThat(result.outcome().charge()).isEqualTo(CancellationOutcome.Charge.NONE);
        assertThat(service.create(cmd(tenant, 2, at(19, 0))).await().indefinitely().status()).isEqualTo("CONFIRMED");
    }

    @Test
    void lateCancellationChargesAndManagerWaiverRemovesTheCharge() {
        final var a = service.create(cmd(tenant, 2, at(19, 0))).await().indefinitely();
        now.set(at(19, 0).minus(Duration.ofHours(2)));
        final var charged = service.cancel(tenant, a.id(), "sorry", false).await().indefinitely();
        assertThat(charged.outcome().charge()).isEqualTo(CancellationOutcome.Charge.LATE_FEE);
        assertThat(charged.outcome().chargeAmount()).isEqualTo(Money.of(100_000, "IDR"));

        now.set(Instant.parse("2026-10-07T03:00:00Z"));
        final var b = service.create(cmd(tenant, 2, at(19, 0))).await().indefinitely();
        now.set(at(19, 0).minus(Duration.ofHours(2)));
        final var waived = service.cancel(tenant, b.id(), "sorry", true).await().indefinitely();
        assertThat(waived.outcome().charge()).isEqualTo(CancellationOutcome.Charge.NONE);
    }

    @Test
    void noShowAfterGraceReleasesTheTableAndChargesTheFee() {
        final var a = service.create(cmd(tenant, 2, at(19, 0))).await().indefinitely();
        now.set(at(19, 15));
        final var r = service.markNoShow(tenant, a.id(), false).await().indefinitely();
        assertThat(r.reservation().status()).isEqualTo("NO_SHOW");
        assertThat(r.outcome().charge()).isEqualTo(CancellationOutcome.Charge.LATE_FEE);
        final var ledger = ledgers.find(tenant, LedgerId.forVenueDay(venue, SAT)).await().indefinitely().orElseThrow();
        assertThat(ledger.allocations()).isEmpty(); // table released for walk-ins
    }

    @Test
    void seatThenComplete() {
        final var a = service.create(cmd(tenant, 2, at(19, 0))).await().indefinitely();
        now.set(at(18, 50));
        assertThat(service.seat(tenant, a.id()).await().indefinitely().status()).isEqualTo("SEATED");
        assertThat(service.complete(tenant, a.id()).await().indefinitely().status()).isEqualTo("COMPLETED");
    }

    @Test
    void largePartyFlowsThroughDepositConfirmation() {
        tables = List.of(new TableInfo(DiningTableId.generate(), "BIG", 4, 10, Set.of()));
        final var v = service.create(cmd(tenant, 8, at(19, 0))).await().indefinitely();
        assertThat(v.status()).isEqualTo("PENDING_DEPOSIT");
        assertThat(v.depositAmount()).isEqualByComparingTo("800000");
        assertThat(service.confirmDeposit(tenant, v.id(), "pay-77").await().indefinitely().status()).isEqualTo("CONFIRMED");
    }

    // ---------------------------------------------------------------- reschedule

    @Test
    void sameDayRescheduleMovesTheBookingAndFreesTheOldSlot() {
        final var a = service.create(cmd(tenant, 2, at(18, 0))).await().indefinitely();
        final var moved = service.reschedule(tenant, a.id(), at(20, 0), 2).await().indefinitely();
        assertThat(moved.startsAt()).isEqualTo(at(20, 0));
        final var slots = service.availableSlots(tenant, venue.value(), SAT, 2).await().indefinitely();
        assertThat(slots).contains(at(18, 0)).doesNotContain(at(20, 0));
    }

    @Test
    void failedRescheduleLeavesTheOriginalBookingUntouched() {
        final var a = service.create(cmd(tenant, 2, at(18, 0))).await().indefinitely();
        service.create(cmd(tenant, 2, at(20, 0))).await().indefinitely();
        assertThatThrownBy(() -> service.reschedule(tenant, a.id(), at(20, 30), 2).await().indefinitely())
                .isInstanceOf(NoAvailabilityException.class);
        final var still = service.get(tenant, a.id()).await().indefinitely();
        assertThat(still.startsAt()).isEqualTo(at(18, 0));
        assertThat(still.tableIds()).hasSize(1);
        assertThat(service.availableSlots(tenant, venue.value(), SAT, 2).await().indefinitely()).doesNotContain(at(18, 0));
    }

    @Test
    void crossDayRescheduleMovesBetweenLedgers() {
        final var a = service.create(cmd(tenant, 2, at(18, 0))).await().indefinitely();
        final var nextDay = SAT.plusDays(1).atTime(18, 0).atZone(JKT).toInstant();
        service.reschedule(tenant, a.id(), nextDay, 2).await().indefinitely();
        assertThat(service.availableSlots(tenant, venue.value(), SAT, 2).await().indefinitely()).contains(at(18, 0));
        assertThat(service.availableSlots(tenant, venue.value(), SAT.plusDays(1), 2).await().indefinitely()).doesNotContain(nextDay);
    }

    // ---------------------------------------------------------------- tenant isolation

    @Test
    void anotherTenantCannotReadOrCancelMyReservation() {
        final var a = service.create(cmd(tenant, 2, at(19, 0))).await().indefinitely();
        final var intruder = UUID.randomUUID();
        assertThatThrownBy(() -> service.get(intruder, a.id()).await().indefinitely()).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.cancel(intruder, a.id(), "x", true).await().indefinitely()).isInstanceOf(NotFoundException.class);
        assertThat(service.get(tenant, a.id()).await().indefinitely().status()).isEqualTo("CONFIRMED");
    }

    @Test
    void tenantsHaveIndependentAvailabilityForTheSameVenueId() {
        service.create(cmd(tenant, 2, at(19, 0))).await().indefinitely();
        assertThat(service.create(cmd(UUID.randomUUID(), 2, at(19, 0))).await().indefinitely().status()).isEqualTo("CONFIRMED");
    }

    // ---------------------------------------------------------------- waitlist

    @Test
    void waitlistNotifiesTheLongestWaitingPartyThatFitsAndExpiresStaleHolds() {
        final var wl = new WaitlistService(new InMemoryWaitlistRepository(), uow, sink, now::get, Duration.ofMinutes(60), Duration.ofMinutes(10));
        final var big = wl.join(tenant, venue.value(), "Big Party", "+62 811 1111 1111", null, 6, 4).await().indefinitely();
        now.set(now.get().plusSeconds(60));
        final var small1 = wl.join(tenant, venue.value(), "Small One", "+62 811 2222 2222", null, 2, 4).await().indefinitely();
        now.set(now.get().plusSeconds(60));
        final var small2 = wl.join(tenant, venue.value(), "Small Two", "+62 811 3333 3333", null, 2, 4).await().indefinitely();

        final var first = wl.notifyNext(tenant, venue.value(), 2).await().indefinitely();
        assertThat(first).isPresent();
        assertThat(first.get().id()).isEqualTo(small1.id()); // big party does not fit a 2-top, skipped not dropped

        now.set(now.get().plus(Duration.ofMinutes(11))); // small1 never showed
        final var second = wl.notifyNext(tenant, venue.value(), 2).await().indefinitely();
        assertThat(second.orElseThrow().id()).isEqualTo(small2.id());
        assertThat(wl.queue(tenant, venue.value()).await().indefinitely()).extracting(WaitlistService.View::status)
                .containsExactly("WAITING", "NOTIFIED"); // big still waiting, small1 expired and gone

        assertThat(wl.notifyNext(tenant, venue.value(), 1).await().indefinitely()).isEmpty();
    }

    @Test
    void waitlistQuotesGrowAsTheQueueGrows() {
        final var wl = new WaitlistService(new InMemoryWaitlistRepository(), uow, sink, now::get, Duration.ofMinutes(60), Duration.ofMinutes(10));
        final var a = wl.join(tenant, venue.value(), "A", "+62 811 1111 1111", null, 2, 2).await().indefinitely();
        wl.join(tenant, venue.value(), "B", "+62 811 1111 1112", null, 2, 2).await().indefinitely();
        wl.join(tenant, venue.value(), "C", "+62 811 1111 1113", null, 2, 2).await().indefinitely();
        final var d = wl.join(tenant, venue.value(), "D", "+62 811 1111 1114", null, 2, 2).await().indefinitely();
        assertThat(d.quotedWaitMinutes()).isGreaterThan(a.quotedWaitMinutes());
    }
}
