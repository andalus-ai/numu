package tech.kayys.numou.reservation.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationDomainTest {

    private final FacilityId facilityId = FacilityId.generate();
    private final ZoneId zone = ZoneId.of("Asia/Jakarta");
    private final Instant now = Instant.parse("2026-10-09T10:00:00Z");
    private BookingRules rules;

    @BeforeEach
    void setUp() {
        final var schedule = Map.of(
                DayOfWeek.FRIDAY, List.of(new OperatingWindow("Dinner", LocalTime.of(17, 0), LocalTime.of(23, 0), LocalTime.of(21, 30)))
        );
        rules = new BookingRules(
                zone, 1, 12, 30,
                Duration.ofMinutes(60), Duration.ofMinutes(90), Duration.ofMinutes(15),
                Map.of(2, Duration.ofMinutes(90), 6, Duration.ofMinutes(120)),
                5, Money.of(100_000, "IDR"),
                Duration.ofHours(24), Money.of(150_000, "IDR"),
                Duration.ofMinutes(15), Duration.ofMinutes(15), schedule
        );
    }

    @Test
    void requestAndConfirmBooking() {
        final var start = LocalDateTime.of(2026, 10, 9, 18, 0).atZone(zone).toInstant();
        final var customer = new CustomerContact("John Doe", "+62812345678", "john@example.com");

        final var r = Reservation.request(
                ReservationId.generate(), facilityId, customer, PartySize.of(2),
                start, BookingSource.WEB, "Quiet corner please", Set.of("Window seat"), rules, now
        );

        assertThat(r.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(r.duration()).isEqualTo(Duration.ofMinutes(90));
        assertThat(r.requirements()).containsExactly("Window seat");
    }

    @Test
    void largePartyRequiresDeposit() {
        final var start = LocalDateTime.of(2026, 10, 9, 18, 0).atZone(zone).toInstant();
        final var customer = new CustomerContact("Alice", "+62812345679", "");

        final var r = Reservation.request(
                ReservationId.generate(), facilityId, customer, PartySize.of(6),
                start, BookingSource.PHONE, "", Set.of(), rules, now
        );

        assertThat(r.status()).isEqualTo(ReservationStatus.PENDING_DEPOSIT);
        assertThat(r.deposit().amount()).isEqualByComparingTo("600000");

        r.confirmDeposit("PAY-1234", now);
        assertThat(r.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(r.depositPaid()).isTrue();
    }

    @Test
    void ledgerAllocatesBestFitSingleAndCombined() {
        final var day = LocalDate.of(2026, 10, 9);
        final var ledger = AvailabilityLedger.open(facilityId, day, now);

        final var r1 = ResourceId.generate();
        final var r2 = ResourceId.generate();
        final var r3 = ResourceId.generate();

        final var resources = List.of(
                new BookableResource(r1, "Desk 1", 1, 2, Set.of(r2)),
                new BookableResource(r2, "Desk 2", 1, 2, Set.of(r1)),
                new BookableResource(r3, "Conference Table", 4, 8, Set.of())
        );

        final var start = LocalDateTime.of(2026, 10, 9, 18, 0).atZone(zone).toInstant();

        // Single fit for 2
        final var alloc1 = ledger.allocate(ReservationId.generate(), PartySize.of(2), start, rules, resources, now);
        assertThat(alloc1.resourceIds()).hasSize(1);

        // Combined fit for 4 when large resource not combinable or to test combining Desk 1 + Desk 2
        final var smallResources = List.of(
                new BookableResource(r1, "Desk 1", 1, 2, Set.of(r2)),
                new BookableResource(r2, "Desk 2", 1, 2, Set.of(r1))
        );
        final var ledger2 = AvailabilityLedger.open(facilityId, day, now);
        final var alloc2 = ledger2.allocate(ReservationId.generate(), PartySize.of(4), start, rules, smallResources, now);
        assertThat(alloc2.resourceIds()).containsExactlyInAnyOrder(r1, r2);
    }

    @Test
    void doubleBookingThrowsNoAvailability() {
        final var day = LocalDate.of(2026, 10, 9);
        final var ledger = AvailabilityLedger.open(facilityId, day, now);
        final var r1 = ResourceId.generate();
        final var resources = List.of(new BookableResource(r1, "Room A", 1, 4, Set.of()));
        final var start = LocalDateTime.of(2026, 10, 9, 18, 0).atZone(zone).toInstant();

        ledger.allocate(ReservationId.generate(), PartySize.of(2), start, rules, resources, now);

        assertThatThrownBy(() -> ledger.allocate(ReservationId.generate(), PartySize.of(2), start, rules, resources, now))
                .isInstanceOf(NoAvailabilityException.class);
    }

    @Test
    void checkInAndComplete() {
        final var start = LocalDateTime.of(2026, 10, 9, 18, 0).atZone(zone).toInstant();
        final var customer = new CustomerContact("Bob", "+62812345670", "");
        final var r = Reservation.request(
                ReservationId.generate(), facilityId, customer, PartySize.of(2),
                start, BookingSource.WEB, "", Set.of(), rules, now
        );

        final var resourceId = ResourceId.generate();
        r.assignResources(Set.of(resourceId), now);

        final var arrivalTime = start.minus(Duration.ofMinutes(10));
        r.checkIn(arrivalTime, rules);
        assertThat(r.status()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThat(r.checkedInAt()).isEqualTo(arrivalTime);

        r.complete(start.plus(Duration.ofMinutes(60)));
        assertThat(r.status()).isEqualTo(ReservationStatus.COMPLETED);
    }

    @Test
    void waitlistFlow() {
        final var customer = new CustomerContact("Charlie", "+628111222", "");
        final var entry = WaitlistEntry.join(WaitlistEntryId.generate(), facilityId, customer, PartySize.of(2), "Urgent", now);

        assertThat(entry.status()).isEqualTo(WaitlistEntry.Status.WAITING);

        entry.notifyCustomer(Duration.ofMinutes(10), now);
        assertThat(entry.status()).isEqualTo(WaitlistEntry.Status.NOTIFIED);

        entry.checkIn(now.plus(Duration.ofMinutes(5)));
        assertThat(entry.status()).isEqualTo(WaitlistEntry.Status.ADMITTED);
    }
}
