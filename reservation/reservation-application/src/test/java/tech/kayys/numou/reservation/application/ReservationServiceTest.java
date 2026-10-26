package tech.kayys.numou.reservation.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.reservation.adapter.memory.InMemoryReservationRepositories;
import tech.kayys.numou.reservation.domain.*;
import tech.kayys.numou.reservation.spi.BookingRulesPort;
import tech.kayys.numou.reservation.spi.DomainEventSink;
import tech.kayys.numou.reservation.spi.ResourceDirectoryPort;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID facility = UUID.randomUUID();
    private final ZoneId zone = ZoneId.of("Asia/Jakarta");
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-09T06:00:00Z"));
    private final DomainClock clock = now::get;
    private final AtomicInteger publishedEventCount = new AtomicInteger();

    private InMemoryReservationRepositories.Reservations reservations;
    private InMemoryReservationRepositories.Ledgers ledgers;
    private ReservationService service;

    private ResourceId room1;
    private ResourceId room2;

    @BeforeEach
    void setUp() {
        reservations = new InMemoryReservationRepositories.Reservations();
        ledgers = new InMemoryReservationRepositories.Ledgers();

        room1 = ResourceId.generate();
        room2 = ResourceId.generate();

        final var resourceList = List.of(
                new BookableResource(room1, "Room 101", 1, 2, Set.of(room2)),
                new BookableResource(room2, "Room 102", 1, 2, Set.of(room1))
        );

        final ResourceDirectoryPort directory = (t, f) -> Uni.createFrom().item(resourceList);

        final var schedule = Map.of(
                DayOfWeek.FRIDAY, List.of(new OperatingWindow("Day", LocalTime.of(8, 0), LocalTime.of(22, 0), LocalTime.of(20, 0)))
        );

        final var rules = new BookingRules(
                zone, 1, 10, 30,
                Duration.ofMinutes(30), Duration.ofMinutes(60), Duration.ofMinutes(15),
                Map.of(2, Duration.ofMinutes(60)),
                4, Money.of(100_000, "IDR"),
                Duration.ofHours(24), Money.of(50_000, "IDR"),
                Duration.ofMinutes(15), Duration.ofMinutes(15), schedule
        );

        final BookingRulesPort rulesPort = (t, f) -> Uni.createFrom().item(rules);
        final UnitOfWork uow = new UnitOfWork() {
            @Override
            public <R> Uni<R> execute(Supplier<Uni<R>> work) {
                return work.get();
            }
        };
        publishedEventCount.set(0);
        final DomainEventSink events = (t, evts) -> {
            publishedEventCount.addAndGet(evts.size());
            return Uni.createFrom().voidItem();
        };

        service = new ReservationService(reservations, ledgers, directory, rulesPort, uow, events, clock);
    }

    @Test
    void bookingWorkflow() {
        final var start = LocalDateTime.of(2026, 10, 9, 14, 0).atZone(zone).toInstant();
        final var customer = new CustomerContact("Jane Doe", "+6281112233", "jane@example.com");

        final var view = service.book(
                tenant, facility, customer, 2, start, BookingSource.WEB, "VIP", Set.of("Projector")
        ).await().indefinitely();

        assertThat(view).isNotNull();
        assertThat(view.status()).isEqualTo("CONFIRMED");
        assertThat(view.resourceIds()).hasSize(1);

        // Check in
        now.set(start.minus(Duration.ofMinutes(5)));
        service.checkIn(tenant, view.id()).await().indefinitely();

        final var checkedIn = service.booking(tenant, view.id()).await().indefinitely();
        assertThat(checkedIn.status()).isEqualTo("CHECKED_IN");

        // Complete
        service.complete(tenant, view.id()).await().indefinitely();
        final var completed = service.booking(tenant, view.id()).await().indefinitely();
        assertThat(completed.status()).isEqualTo("COMPLETED");
    }

    @Test
    void constrainedBookingReplayDoesNotAllocateOrPublishEventsTwice() {
        final var start = LocalDateTime.of(2026, 10, 9, 14, 0).atZone(zone).toInstant();
        final var customer = new CustomerContact("Workshop Customer", "+6281112233", "customer@example.com");

        final var first = service.bookWithConstraints(tenant, facility, customer, 1, start,
                Duration.ofMinutes(60), Set.of("LIFT"), BookingSource.WEB, "oil change",
                Set.of("vehicle-service"), "vehicle-booking-42").await().indefinitely();
        final int eventsAfterCreate = publishedEventCount.get();

        final var replay = service.bookWithConstraints(tenant, facility, customer, 1, start,
                Duration.ofMinutes(60), Set.of("LIFT"), BookingSource.WEB, "oil change",
                Set.of("vehicle-service"), "vehicle-booking-42").await().indefinitely();

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(reservations.allOf(tenant)).hasSize(1);
        assertThat(publishedEventCount.get()).isEqualTo(eventsAfterCreate);
    }

    @Test
    void availableSlotsQuery() {
        final var day = LocalDate.of(2026, 10, 9);
        final var slots = service.availableSlots(tenant, facility, day, 2).await().indefinitely();
        assertThat(slots).isNotEmpty();
    }
}
