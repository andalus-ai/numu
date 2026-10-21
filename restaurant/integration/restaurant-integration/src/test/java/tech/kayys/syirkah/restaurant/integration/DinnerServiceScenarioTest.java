package tech.kayys.syirkah.restaurant.integration;

import tech.kayys.syirkah.aggregate.memory.DispatchingEventSink;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.cache.CachePolicy;
import tech.kayys.syirkah.cache.adapter.CaffeineCache;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.restaurant.kernel.domain.*;
import tech.kayys.syirkah.aggregate.domain.*;
import tech.kayys.syirkah.restaurant.kitchen.adapter.memory.*;
import tech.kayys.syirkah.restaurant.kitchen.application.KitchenService;
import tech.kayys.syirkah.restaurant.order.adapter.memory.InMemoryOrderRepository;
import tech.kayys.syirkah.restaurant.order.application.OrderService;
import tech.kayys.syirkah.restaurant.reservation.adapter.memory.*;
import tech.kayys.syirkah.restaurant.reservation.application.*;
import tech.kayys.syirkah.restaurant.reservation.domain.*;
import tech.kayys.syirkah.restaurant.venue.adapter.memory.InMemoryVenueRepositories;
import tech.kayys.syirkah.restaurant.venue.application.MenuView;
import tech.kayys.syirkah.restaurant.venue.application.VenueService;
import tech.kayys.syirkah.restaurant.venue.domain.MenuItem;
import tech.kayys.syirkah.restaurant.venue.domain.TableStatus;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** One whole evening through every bounded context, wired exactly as the monolith/dev profile wires them. */
class DinnerServiceScenarioTest {

    static final ZoneId JKT = ZoneId.of("Asia/Jakarta");
    static final LocalDate SAT = LocalDate.of(2026, 10, 10);
    static Instant at(int h, int m) { return SAT.atTime(h, m).atZone(JKT).toInstant(); }

    final UUID tenant = UUID.randomUUID();
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-07T03:00:00Z"));
    final UnitOfWork uow = new UnitOfWork() { public <R> Uni<R> execute(java.util.function.Supplier<Uni<R>> w) { return w.get(); } };
    final DispatchingEventSink bus = new DispatchingEventSink();

    InMemoryVenueRepositories.Venues venues = new InMemoryVenueRepositories.Venues();
    InMemoryVenueRepositories.Tables tables = new InMemoryVenueRepositories.Tables();
    InMemoryVenueRepositories.Menus menus = new InMemoryVenueRepositories.Menus();
    CaffeineCache<MenuView> menuCache = new CaffeineCache<>(VenueService.MENU_CACHE, CachePolicy.referenceData());
    VenueService venue;
    KitchenService kitchen;
    OrderService orders;
    ReservationService reservations;
    WaitlistService waitlist;

    UUID venueId, grill, bar, t1, t2, rendang, mojito, dessert, menuId;

    static BookingRules rules() {
        final var periods = new EnumMap<DayOfWeek, List<ServicePeriod>>(DayOfWeek.class);
        for (var d : DayOfWeek.values()) periods.put(d, List.of(new ServicePeriod("dinner", LocalTime.of(17, 0), LocalTime.of(21, 0))));
        return new BookingRules(JKT, 1, 12, 60, Duration.ofMinutes(60), Duration.ofMinutes(30), Duration.ofMinutes(15),
                Map.of(2, Duration.ofMinutes(75), 4, Duration.ofMinutes(90), 99, Duration.ofMinutes(120)), 8, Money.of(100_000, "IDR"),
                Duration.ofHours(24), Money.of(50_000, "IDR"), Duration.ofMinutes(15), Duration.ofMinutes(15), periods);
    }

    @BeforeEach
    void setUp() {
        final var clock = (tech.kayys.syirkah.foundation.domain.time.DomainClock) now::get;
        venue = new VenueService(venues, tables, menus, uow, bus, clock, menuCache);
        kitchen = new KitchenService(new InMemoryTicketRepository(), new InMemoryAvailabilityBoardRepository(),
                (t, v, s) -> Uni.createFrom().item(2), uow, bus, clock);
        orders = new OrderService(new InMemoryOrderRepository(), VenueBackedPorts.menuLookup(menus), VenueBackedPorts.availability(kitchen),
                VenueBackedPorts.policy(venues), VenueBackedPorts.seating(tables, bus, clock), uow, bus, clock);
        waitlist = new WaitlistService(new InMemoryWaitlistRepository(), uow, bus, clock, Duration.ofMinutes(60), Duration.ofMinutes(10));
        reservations = new ReservationService(new InMemoryReservationRepository(), new InMemoryLedgerRepository(),
                VenueBackedPorts.bookingRules(t -> rules()), VenueBackedPorts.tableDirectory(tables), uow, bus, clock);
        RestaurantPolicies.register(bus, kitchen, orders, waitlist);

        venueId = venue.createVenue(tenant, "Warung Maju").await().indefinitely();
        grill = venue.addStation(tenant, venueId, "Grill", 2).await().indefinitely();
        bar = venue.addStation(tenant, venueId, "Bar", 2).await().indefinitely();
        t1 = venue.addTable(tenant, venueId, "T1", "main", 2, 4, Set.of()).await().indefinitely();
        t2 = venue.addTable(tenant, venueId, "T2", "main", 2, 4, Set.of()).await().indefinitely();
        menuId = venue.createMenu(tenant, venueId, "Dinner").await().indefinitely();
        venue.addSection(tenant, menuId, "Mains").await().indefinitely();
        venue.addSection(tenant, menuId, "Drinks").await().indefinitely();
        rendang = venue.addItem(tenant, menuId, "Mains", "Rendang", "", Money.of(85_000, "IDR"), grill, Duration.ofMinutes(12),
                Set.of("coconut"), List.of(new MenuItem.Modifier("extra spicy", Money.of(0, "IDR")))).await().indefinitely();
        dessert = venue.addItem(tenant, menuId, "Mains", "Klepon", "", Money.of(25_000, "IDR"), grill, Duration.ofMinutes(5),
                Set.of(), List.of()).await().indefinitely();
        mojito = venue.addItem(tenant, menuId, "Drinks", "Mojito", "", Money.of(35_000, "IDR"), bar, Duration.ofMinutes(3),
                Set.of(), List.of(new MenuItem.Modifier("double shot", Money.of(10_000, "IDR")))).await().indefinitely();
        venue.publishMenu(tenant, menuId).await().indefinitely();
    }

    @Test
    void aWholeEveningFromBookingToCleanedTable() {
        // --- guests book and arrive
        final var booking = reservations.create(new ReservationService.CreateCommand(tenant, venueId, "Budi Santoso", "+62 812 3456 7890", null,
                2, at(19, 0), BookingSource.WEB, "", Set.of())).await().indefinitely();
        assertThat(booking.status()).isEqualTo("CONFIRMED");
        now.set(at(18, 55));
        reservations.seat(tenant, booking.id()).await().indefinitely();
        final var tableId = booking.tableIds().get(0);

        // --- walk-in party waits while the room is full
        waitlist.join(tenant, venueId, "Walk In", "+62 811 0000 0000", null, 2, 2).await().indefinitely();

        // --- waiter opens the check on the reserved table and takes the order
        final var order = orders.open(tenant, venueId, Set.of(tableId), "Sari", 2).await().indefinitely();
        assertThat(tables.find(tenant, DiningTableId.of(tableId)).await().indefinitely().orElseThrow().status()).isEqualTo(TableStatus.OCCUPIED);
        assertThatThrownBy(() -> orders.open(tenant, venueId, Set.of(tableId), "Sari", 2).await().indefinitely()).isInstanceOf(RuntimeException.class); // table taken

        orders.addItem(tenant, order.id(), rendang, 2, List.of("extra spicy"), "no peanuts", 1).await().indefinitely();
        final var withDrinks = orders.addItem(tenant, order.id(), mojito, 2, List.of("double shot"), "", 1).await().indefinitely();
        assertThat(withDrinks.totals().subtotal()).isEqualByComparingTo("260000"); // 2x85,000 + 2x(35,000+10,000), priced server-side

        // --- fire: kitchen tickets appear by station, via the order's event
        orders.fire(tenant, order.id(), 1).await().indefinitely();
        assertThat(bus.failures()).isEmpty();
        final var queue = kitchen.queue(tenant, venueId, Optional.empty()).await().indefinitely();
        assertThat(queue).hasSize(2);
        assertThat(queue).extracting(t -> t.stationId()).containsExactlyInAnyOrder(grill, bar);
        assertThat(queue.stream().filter(t -> t.stationId().equals(grill)).findFirst().orElseThrow().hasAllergens()).isTrue();

        // --- kitchen cooks; order lines flip to READY through the kitchen's events
        for (var ticket : queue) {
            for (var item : ticket.items()) {
                kitchen.start(tenant, ticket.id(), item.lineId()).await().indefinitely();
                kitchen.markReady(tenant, ticket.id(), item.lineId()).await().indefinitely();
            }
            kitchen.bump(tenant, ticket.id()).await().indefinitely();
        }
        assertThat(bus.failures()).isEmpty();
        assertThat(orders.get(tenant, order.id()).await().indefinitely().lines()).allMatch(l -> l.status().equals("READY"));
        assertThat(kitchen.queue(tenant, venueId, Optional.empty()).await().indefinitely()).isEmpty();

        // --- the kitchen runs out of mojitos: the next order is refused
        kitchen.eightySix(tenant, venueId, mojito, "sold out", null).await().indefinitely();
        assertThatThrownBy(() -> orders.addItem(tenant, order.id(), mojito, 1, null, "", 2).await().indefinitely())
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("sold out");

        // --- dessert is ordered, fired and then voided with manager approval: the kitchen ticket disappears
        final var withDessert = orders.addItem(tenant, order.id(), dessert, 1, null, "", 2).await().indefinitely();
        final var dessertLine = withDessert.lines().stream().filter(l -> l.name().equals("Klepon")).findFirst().orElseThrow().id();
        orders.fire(tenant, order.id(), 2).await().indefinitely();
        assertThat(kitchen.queue(tenant, venueId, Optional.empty()).await().indefinitely()).hasSize(1);
        orders.voidLine(tenant, order.id(), dessertLine, "guest changed mind", "manager-1").await().indefinitely();
        assertThat(kitchen.queue(tenant, venueId, Optional.empty()).await().indefinitely()).isEmpty();
        assertThat(bus.failures()).isEmpty();

        // --- bill, split three ways, pay
        final var billed = orders.requestBill(tenant, order.id()).await().indefinitely();
        assertThat(billed.totals().total()).isEqualByComparingTo("303030"); // 260,000 + 13,000 service + 30,030 tax (11% of 273,000)
        final var shares = orders.splitBill(tenant, order.id(), 3).await().indefinitely();
        assertThat(shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(billed.totals().total());
        orders.settle(tenant, order.id(), "pay-001").await().indefinitely();
        assertThat(tables.find(tenant, DiningTableId.of(tableId)).await().indefinitely().orElseThrow().status()).isEqualTo(TableStatus.DIRTY);

        // --- busser cleans the table: the waiting party is told it is ready
        venue.markTableClean(tenant, tableId).await().indefinitely();
        final var queueNow = waitlist.queue(tenant, venueId).await().indefinitely();
        assertThat(queueNow).hasSize(1);
        assertThat(queueNow.get(0).status()).isEqualTo("NOTIFIED");
        assertThat(bus.failures()).isEmpty();

        // --- the event stream tells the whole story, in tenant, with no guest personal data
        assertThat(bus.types()).contains("restaurant.reservation.confirmed", "restaurant.order.course-fired", "restaurant.kitchen.ticket.created",
                "restaurant.kitchen.item.ready", "restaurant.kitchen.ticket.bumped", "restaurant.kitchen.item.eighty-sixed",
                "restaurant.order.line-voided", "restaurant.order.paid", "restaurant.table.cleaned", "restaurant.waitlist.notified");
        assertThat(bus.all()).allMatch(p -> p.tenantId().equals(tenant));
        final var dump = bus.all().toString();
        assertThat(dump).doesNotContain("Budi", "Santoso", "3456 7890", "Walk In", "0000 0000");
    }

    @Test
    void publishedMenuIsServedFromCacheAndRefreshedWhenThePriceChanges() {
        final var first = venue.publishedMenu(tenant, venueId).await().indefinitely();
        venue.publishedMenu(tenant, venueId).await().indefinitely();
        assertThat(menuCache.stats().loads()).isEqualTo(1);
        assertThat(menuCache.stats().hits()).isEqualTo(1);
        assertThat(first.sections().get("Mains")).extracting(i -> i.name()).contains("Rendang");

        venue.changePrice(tenant, menuId, rendang, Money.of(90_000, "IDR")).await().indefinitely();
        final var after = venue.publishedMenu(tenant, venueId).await().indefinitely();
        assertThat(after.sections().get("Mains").stream().filter(i -> i.name().equals("Rendang")).findFirst().orElseThrow().price()).isEqualByComparingTo("90000");
    }

    @Test
    void anotherTenantNeverSeesOrOrdersFromThisMenu() {
        final var other = UUID.randomUUID();
        venue.publishedMenu(tenant, venueId).await().indefinitely(); // warm this tenant's cache
        assertThat(venue.publishedMenu(other, venueId).await().indefinitely()).isNull();
        assertThatThrownBy(() -> orders.open(other, venueId, Set.of(t1), "x", 2).await().indefinitely()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void priceChangeAfterOrderingDoesNotRepriceTheCheck() {
        final var o = orders.open(tenant, venueId, Set.of(t1), "Sari", 2).await().indefinitely();
        orders.addItem(tenant, o.id(), rendang, 1, null, "", 1).await().indefinitely();
        venue.changePrice(tenant, menuId, rendang, Money.of(200_000, "IDR")).await().indefinitely();
        assertThat(orders.get(tenant, o.id()).await().indefinitely().totals().subtotal()).isEqualByComparingTo("85000");
    }

    @Test
    void blockedTablesAreNotSoldToReservations() {
        venue.blockTable(tenant, t1, "water leak").await().indefinitely();
        venue.blockTable(tenant, t2, "private event").await().indefinitely();
        assertThatThrownBy(() -> reservations.create(new ReservationService.CreateCommand(tenant, venueId, "A", "+62 811 1111 1111", null,
                2, at(19, 0), BookingSource.WEB, "", Set.of())).await().indefinitely()).isInstanceOf(SlotUnavailableException.class);
    }

    @Test
    void settlingTwiceOrPayingBeforeBillingIsRejectedAndTableStaysConsistent() {
        final var o = orders.open(tenant, venueId, Set.of(t1), "Sari", 2).await().indefinitely();
        orders.addItem(tenant, o.id(), rendang, 1, null, "", 1).await().indefinitely();
        orders.fire(tenant, o.id(), 1).await().indefinitely();
        assertThatThrownBy(() -> orders.settle(tenant, o.id(), "pay-1").await().indefinitely()).isInstanceOf(RuntimeException.class);
        assertThat(tables.find(tenant, DiningTableId.of(t1)).await().indefinitely().orElseThrow().status()).isEqualTo(TableStatus.OCCUPIED);
    }
}
