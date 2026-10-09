package tech.kayys.syirkah.restaurant.reservation.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.restaurant.kernel.domain.*;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationDomainTest {

    static final ZoneId JKT = ZoneId.of("Asia/Jakarta");
    static final LocalDate SAT = LocalDate.of(2026, 10, 10);
    static final Instant NOW = Instant.parse("2026-10-07T03:00:00Z"); // Wed 10:00 WIB
    static final VenueId VENUE = VenueId.generate();

    static Instant at(int h, int m) { return SAT.atTime(h, m).atZone(JKT).toInstant(); }

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

    static TableInfo table(String label, int min, int max, TableInfo... joinable) {
        return new TableInfo(DiningTableId.generate(), label, min, max, Set.of());
    }

    static TableInfo joined(String label, int min, int max, DiningTableId... with) {
        return new TableInfo(DiningTableId.generate(), label, min, max, new HashSet<>(Arrays.asList(with)));
    }

    static GuestContact guest() { return new GuestContact("Budi Santoso", "+62 812 3456 7890", "budi@example.com"); }

    AvailabilityLedger ledger() { return AvailabilityLedger.open(VENUE, SAT, NOW); }

    // ---------- ledger: allocation ----------

    @Test
    void picksTheTightestSingleTable() {
        final var two = table("A1", 1, 2); final var four = table("B1", 2, 4); final var six = table("C1", 4, 6);
        final var l = ledger();
        final var a2 = l.allocate(ReservationId.generate(), PartySize.of(2), at(18, 0), rules(), List.of(six, four, two), NOW);
        final var a3 = l.allocate(ReservationId.generate(), PartySize.of(3), at(18, 0), rules(), List.of(six, four, two), NOW);
        assertThat(a2.tableIds()).containsExactly(two.id());
        assertThat(a3.tableIds()).containsExactly(four.id());
    }

    @Test
    void partyBelowTableMinimumDoesNotTakeThatTable() {
        final var big = table("C1", 4, 6);
        assertThatThrownBy(() -> ledger().allocate(ReservationId.generate(), PartySize.of(1), at(18, 0), rules(), List.of(big), NOW))
                .isInstanceOf(NoAvailabilityException.class);
    }

    @Test
    void overlappingBookingsCannotShareATable() {
        final var only = table("A1", 1, 4); final var l = ledger();
        l.allocate(ReservationId.generate(), PartySize.of(2), at(18, 0), rules(), List.of(only), NOW); // blocked 18:00-19:30
        assertThatThrownBy(() -> l.allocate(ReservationId.generate(), PartySize.of(2), at(19, 0), rules(), List.of(only), NOW))
                .isInstanceOf(NoAvailabilityException.class);
    }

    @Test
    void tableIsReusableExactlyWhenTurnPlusCleanupHasElapsed() {
        final var only = table("A1", 1, 4); final var l = ledger();
        l.allocate(ReservationId.generate(), PartySize.of(2), at(18, 0), rules(), List.of(only), NOW); // 75m + 15m => free at 19:30
        assertThat(l.canAllocate(PartySize.of(2), at(19, 29), rules(), List.of(only))).isFalse();
        assertThat(l.canAllocate(PartySize.of(2), at(19, 30), rules(), List.of(only))).isTrue();
    }

    @Test
    void largerPartiesBlockTheTableLonger() {
        final var only = table("A1", 1, 6); final var l = ledger();
        l.allocate(ReservationId.generate(), PartySize.of(5), at(18, 0), rules(), List.of(only), NOW); // 105m + 15m => 20:00
        assertThat(l.canAllocate(PartySize.of(2), at(19, 59), rules(), List.of(only))).isFalse();
        assertThat(l.canAllocate(PartySize.of(2), at(20, 0), rules(), List.of(only))).isTrue();
    }

    @Test
    void combinesJoinableTablesWhenNoSingleTableFits() {
        final var t1 = joined("D1", 2, 4); final var t2 = joined("D2", 2, 4, t1.id());
        final var a = ledger().allocate(ReservationId.generate(), PartySize.of(7), at(18, 0), rules(), List.of(t1, t2), NOW);
        assertThat(a.tableIds()).containsExactlyInAnyOrder(t1.id(), t2.id());
    }

    @Test
    void doesNotCombineTablesThatCannotBePushedTogether() {
        final var t1 = table("D1", 2, 4); final var t2 = table("D2", 2, 4);
        assertThatThrownBy(() -> ledger().allocate(ReservationId.generate(), PartySize.of(7), at(18, 0), rules(), List.of(t1, t2), NOW))
                .isInstanceOf(NoAvailabilityException.class);
    }

    @Test
    void prefersTheSmallestAdequateCombination() {
        final var a = joined("E1", 2, 4); final var b = joined("E2", 2, 4, a.id()); final var c = joined("E3", 2, 6, a.id());
        final var alloc = ledger().allocate(ReservationId.generate(), PartySize.of(8), at(18, 0), rules(), List.of(a, b, c), NOW);
        assertThat(alloc.tableIds()).containsExactlyInAnyOrder(a.id(), b.id()); // capacity 8 beats 4+6=10
    }

    @Test
    void chainOfThreeTablesCanBeJoinedThroughAMiddleTable() {
        final var a = joined("F1", 2, 4); final var b = joined("F2", 2, 4, a.id()); final var c = joined("F3", 2, 4, b.id());
        final var alloc = ledger().allocate(ReservationId.generate(), PartySize.of(11), at(18, 0), rules(), List.of(a, b, c), NOW);
        assertThat(alloc.tableIds()).hasSize(3);
    }

    @Test
    void allocatingTheSameReservationTwiceIsIdempotent() {
        final var t = table("A1", 1, 4); final var l = ledger(); final var rid = ReservationId.generate();
        final var first = l.allocate(rid, PartySize.of(2), at(18, 0), rules(), List.of(t, table("A2", 1, 4)), NOW);
        final var second = l.allocate(rid, PartySize.of(2), at(18, 0), rules(), List.of(t, table("A2", 1, 4)), NOW);
        assertThat(second).isEqualTo(first);
        assertThat(l.allocations()).hasSize(1);
        assertThat(l.pullDomainEvents()).hasSize(1);
    }

    @Test
    void releaseFreesTheTable() {
        final var only = table("A1", 1, 4); final var l = ledger(); final var rid = ReservationId.generate();
        l.allocate(rid, PartySize.of(2), at(18, 0), rules(), List.of(only), NOW);
        assertThat(l.release(rid, NOW)).isTrue();
        assertThat(l.release(rid, NOW)).isFalse();
        assertThat(l.canAllocate(PartySize.of(2), at(18, 0), rules(), List.of(only))).isTrue();
    }

    @Test
    void startOnAnotherDayIsRejected() {
        assertThatThrownBy(() -> ledger().allocate(ReservationId.generate(), PartySize.of(2), at(18, 0).plus(Duration.ofDays(1)),
                rules(), List.of(table("A1", 1, 4)), NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void availableStartsListsOnlyBookableSlotsInsideServicePeriods() {
        final var only = table("A1", 1, 4); final var l = ledger();
        l.allocate(ReservationId.generate(), PartySize.of(2), at(18, 0), rules(), List.of(only), NOW); // blocks 18:00-19:30
        final var slots = l.availableStarts(PartySize.of(2), rules(), List.of(only), NOW);
        assertThat(slots).contains(at(11, 30), at(14, 0), at(19, 30), at(21, 0));
        assertThat(slots).doesNotContain(at(17, 0), at(17, 30)); // would still be seated when the 18:00 party arrives
        assertThat(slots).doesNotContain(at(18, 0), at(18, 30), at(19, 0), at(14, 30), at(21, 30), at(15, 0));
    }

    @Test
    void availableStartsRespectsMinimumLeadTime() {
        final var only = table("A1", 1, 4);
        final var l = AvailabilityLedger.open(VENUE, SAT, NOW);
        final var almostNow = at(11, 0); // "now" 11:00 on the day => earliest bookable 12:00
        final var slots = l.availableStarts(PartySize.of(2), rules(), List.of(only), almostNow);
        assertThat(slots).doesNotContain(at(11, 30)).contains(at(12, 0));
    }

    @Test
    void ledgerSnapshotRoundTripKeepsAllocations() {
        final var t = table("A1", 1, 4); final var l = ledger();
        l.allocate(ReservationId.generate(), PartySize.of(2), at(18, 0), rules(), List.of(t), NOW);
        final var back = AvailabilityLedger.restore(l.snapshot());
        assertThat(back.allocations()).isEqualTo(l.allocations());
        assertThat(back.id()).isEqualTo(l.id());
        assertThat(back.canAllocate(PartySize.of(2), at(18, 30), rules(), List.of(t))).isFalse();
    }

    @Test
    void ledgerIdIsDeterministicPerVenueAndDay() {
        assertThat(LedgerId.forVenueDay(VENUE, SAT)).isEqualTo(LedgerId.forVenueDay(VENUE, SAT));
        assertThat(LedgerId.forVenueDay(VENUE, SAT)).isNotEqualTo(LedgerId.forVenueDay(VENUE, SAT.plusDays(1)));
        assertThat(LedgerId.forVenueDay(VENUE, SAT)).isNotEqualTo(LedgerId.forVenueDay(VenueId.generate(), SAT));
    }

    // ---------- reservation ----------

    Reservation book(int party, Instant start) {
        return Reservation.request(ReservationId.generate(), VENUE, guest(), PartySize.of(party), start, BookingSource.WEB, "", Set.of(), rules(), NOW);
    }

    @Test
    void smallPartyIsConfirmedImmediately() {
        final var r = book(4, at(19, 0));
        assertThat(r.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(r.deposit()).isNull();
        assertThat(r.pullDomainEvents()).extracting(e -> e.eventType())
                .containsExactly("restaurant.reservation.requested", "restaurant.reservation.confirmed");
    }

    @Test
    void largePartyNeedsDepositBeforeConfirmation() {
        final var r = book(8, at(19, 0));
        assertThat(r.status()).isEqualTo(ReservationStatus.PENDING_DEPOSIT);
        assertThat(r.deposit()).isEqualTo(Money.of(800_000, "IDR"));
        r.confirmDeposit("pay-1", NOW);
        assertThat(r.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(r.depositPaid()).isTrue();
        assertThatThrownBy(() -> r.confirmDeposit("pay-2", NOW)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void bookingRulesAreEnforced() {
        assertThatThrownBy(() -> book(13, at(19, 0))).isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("Party size");
        assertThatThrownBy(() -> Reservation.request(ReservationId.generate(), VENUE, guest(), PartySize.of(2),
                NOW.plus(Duration.ofMinutes(30)), BookingSource.WEB, "", Set.of(), rules(), NOW)).hasMessageContaining("notice");
        assertThatThrownBy(() -> Reservation.request(ReservationId.generate(), VENUE, guest(), PartySize.of(2),
                NOW.plus(Duration.ofDays(61)), BookingSource.WEB, "", Set.of(), rules(), NOW)).hasMessageContaining("ahead");
        assertThatThrownBy(() -> book(2, at(15, 0))).hasMessageContaining("does not take bookings");
        assertThatThrownBy(() -> book(2, at(21, 30))).hasMessageContaining("does not take bookings");
        book(2, at(21, 0)); // last seating itself is allowed
    }

    @Test
    void seatingNeedsTablesAndRespectsTheEarlyWindow() {
        final var r = book(2, at(19, 0)); final var t = table("A1", 1, 4);
        assertThatThrownBy(() -> r.seat(at(19, 0), rules())).isInstanceOf(InvalidStateException.class).hasMessageContaining("No table");
        r.assignTables(Set.of(t.id()), NOW);
        assertThatThrownBy(() -> r.seat(at(18, 44), rules())).hasMessageContaining("Too early");
        r.seat(at(18, 45), rules());
        assertThat(r.status()).isEqualTo(ReservationStatus.SEATED);
        r.complete(at(20, 30));
        assertThat(r.status()).isEqualTo(ReservationStatus.COMPLETED);
    }

    @Test
    void freeCancellationRefundsTheDeposit() {
        final var r = book(8, at(19, 0)); r.confirmDeposit("pay-1", NOW);
        final var outcome = r.cancel("plans changed", at(19, 0).minus(Duration.ofHours(25)), rules());
        assertThat(outcome.charge()).isEqualTo(CancellationOutcome.Charge.NONE);
        assertThat(outcome.refundAmount()).isEqualTo(Money.of(800_000, "IDR"));
        assertThat(r.status()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void lateCancellationForfeitsTheDeposit() {
        final var r = book(8, at(19, 0)); r.confirmDeposit("pay-1", NOW);
        final var outcome = r.cancel("sorry", at(19, 0).minus(Duration.ofHours(2)), rules());
        assertThat(outcome.charge()).isEqualTo(CancellationOutcome.Charge.DEPOSIT_FORFEITED);
        assertThat(outcome.chargeAmount()).isEqualTo(Money.of(800_000, "IDR"));
        assertThat(outcome.refundAmount().amount().signum()).isZero();
    }

    @Test
    void lateCancellationWithoutDepositChargesTheLateFee() {
        final var r = book(4, at(19, 0));
        final var outcome = r.cancel("sorry", at(19, 0).minus(Duration.ofHours(3)), rules());
        assertThat(outcome.charge()).isEqualTo(CancellationOutcome.Charge.LATE_FEE);
        assertThat(outcome.chargeAmount()).isEqualTo(Money.of(200_000, "IDR"));
    }

    @Test
    void cancelledOrSeatedReservationsCannotBeCancelledAgain() {
        final var r = book(2, at(19, 0)); r.cancel("x", NOW, rules());
        assertThatThrownBy(() -> r.cancel("again", NOW, rules())).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void noShowOnlyAfterTheGracePeriodAndForfeitsDeposit() {
        final var r = book(8, at(19, 0)); r.confirmDeposit("pay-1", NOW);
        assertThatThrownBy(() -> r.markNoShow(at(19, 14), rules())).hasMessageContaining("grace");
        final var outcome = r.markNoShow(at(19, 15), rules());
        assertThat(outcome.charge()).isEqualTo(CancellationOutcome.Charge.DEPOSIT_FORFEITED);
        assertThat(r.status()).isEqualTo(ReservationStatus.NO_SHOW);
    }

    @Test
    void seatedGuestsCannotBeMarkedNoShow() {
        final var r = book(2, at(19, 0)); r.assignTables(Set.of(DiningTableId.generate()), NOW); r.seat(at(19, 0), rules());
        assertThatThrownBy(() -> r.markNoShow(at(19, 30), rules())).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void rescheduleClearsTablesAndRevalidates() {
        final var r = book(2, at(19, 0)); r.assignTables(Set.of(DiningTableId.generate()), NOW);
        r.reschedule(at(20, 0), PartySize.of(3), rules(), NOW);
        assertThat(r.tableIds()).isEmpty();
        assertThat(r.startsAt()).isEqualTo(at(20, 0));
        assertThat(r.party().value()).isEqualTo(3);
        assertThatThrownBy(() -> r.reschedule(at(15, 0), PartySize.of(3), rules(), NOW)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void growingPastTheDepositThresholdReturnsToPendingDeposit() {
        final var r = book(4, at(19, 0));
        r.reschedule(at(19, 0), PartySize.of(9), rules(), NOW);
        assertThat(r.status()).isEqualTo(ReservationStatus.PENDING_DEPOSIT);
        assertThat(r.deposit()).isEqualTo(Money.of(900_000, "IDR"));
    }

    @Test
    void domainEventsNeverContainGuestPersonalData() {
        final var r = book(8, at(19, 0)); r.confirmDeposit("pay-1", NOW); r.assignTables(Set.of(DiningTableId.generate()), NOW);
        r.cancel("x", NOW, rules());
        final var dump = r.pullDomainEvents().toString();
        assertThat(dump).doesNotContain("Budi", "Santoso", "budi@", "3456", "pay-1");
    }

    @Test
    void reservationToStringPathDoesNotLeakContactViaGuest() {
        assertThat(book(2, at(19, 0)).guest().toString()).doesNotContain("Budi", "3456", "budi@");
    }

    @Test
    void reservationSnapshotRoundTripPreservesEverything() {
        final var r = book(8, at(19, 0)); r.confirmDeposit("pay-1", NOW);
        final var t = table("A1", 1, 8); r.assignTables(Set.of(t.id()), NOW);
        final var back = Reservation.restore(r.snapshot());
        assertThat(back.snapshot()).isEqualTo(r.snapshot());
        assertThat(back.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(back.guest()).isEqualTo(r.guest());
        assertThat(back.pullDomainEvents()).isEmpty(); // restoring must not replay events
    }

    // ---------- waitlist ----------

    WaitlistEntry waiting() {
        return WaitlistEntry.join(WaitlistEntryId.generate(), VENUE, guest(), PartySize.of(3), Duration.ofMinutes(20), NOW);
    }

    @Test
    void waitlistNotifyThenSeatWithinHold() {
        final var w = waiting();
        w.notifyTableReady(NOW, Duration.ofMinutes(10));
        w.seat(NOW.plus(Duration.ofMinutes(9)));
        assertThat(w.status()).isEqualTo(WaitlistEntry.Status.SEATED);
    }

    @Test
    void waitlistHoldExpires() {
        final var w = waiting();
        w.notifyTableReady(NOW, Duration.ofMinutes(10));
        assertThat(w.expireIfOverdue(NOW.plus(Duration.ofMinutes(9)))).isFalse();
        assertThat(w.expireIfOverdue(NOW.plus(Duration.ofMinutes(11)))).isTrue();
        assertThat(w.status()).isEqualTo(WaitlistEntry.Status.EXPIRED);
        assertThatThrownBy(() -> w.seat(NOW.plus(Duration.ofMinutes(12)))).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void waitlistCannotBeNotifiedTwiceAndLeavingIsTerminal() {
        final var w = waiting(); w.notifyTableReady(NOW, Duration.ofMinutes(10));
        assertThatThrownBy(() -> w.notifyTableReady(NOW, Duration.ofMinutes(10))).isInstanceOf(InvalidStateException.class);
        w.leave(NOW);
        assertThatThrownBy(() -> w.leave(NOW)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void waitQuoteScalesWithQueueAndNeverBelowFiveMinutes() {
        assertThat(WaitlistEntry.quoteWait(0, 4, Duration.ofMinutes(60))).isEqualTo(Duration.ofMinutes(30));
        assertThat(WaitlistEntry.quoteWait(7, 4, Duration.ofMinutes(60))).isEqualTo(Duration.ofMinutes(60));
        assertThat(WaitlistEntry.quoteWait(0, 100, Duration.ofMinutes(6))).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void waitlistSnapshotRoundTrip() {
        final var w = waiting(); w.notifyTableReady(NOW, Duration.ofMinutes(10));
        assertThat(WaitlistEntry.restore(w.snapshot()).snapshot()).isEqualTo(w.snapshot());
    }
}
