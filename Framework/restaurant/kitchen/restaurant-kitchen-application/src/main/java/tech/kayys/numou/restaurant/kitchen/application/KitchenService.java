package tech.kayys.syirkah.restaurant.kitchen.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;
import tech.kayys.syirkah.restaurant.kernel.application.ConflictRetry;
import tech.kayys.syirkah.restaurant.kernel.domain.NotFoundException;
import tech.kayys.syirkah.restaurant.kernel.domain.OrderId;
import tech.kayys.syirkah.restaurant.kernel.domain.StationId;
import tech.kayys.syirkah.restaurant.kernel.domain.TicketId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kernel.spi.DomainEventSink;
import tech.kayys.syirkah.restaurant.kitchen.domain.AvailabilityBoard;
import tech.kayys.syirkah.restaurant.kitchen.domain.ItemState;
import tech.kayys.syirkah.restaurant.kitchen.domain.KitchenTicket;
import tech.kayys.syirkah.restaurant.kitchen.domain.Priority;
import tech.kayys.syirkah.restaurant.kitchen.domain.TicketItem;
import tech.kayys.syirkah.restaurant.kitchen.domain.TicketRouter;
import tech.kayys.syirkah.restaurant.kitchen.spi.AvailabilityBoardRepository;
import tech.kayys.syirkah.restaurant.kitchen.spi.StationDirectoryPort;
import tech.kayys.syirkah.restaurant.kitchen.spi.TicketRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Kitchen use cases: fire a course, drive tickets on the KDS, manage the 86 board. */
public final class KitchenService {

    private static final int MAX_ATTEMPTS = 3;

    public record FiredLine(UUID lineId, UUID menuItemId, String name, int quantity, List<String> modifiers, String notes,
                            Set<String> allergens, long prepSeconds, UUID stationId) { }

    public record FireCommand(UUID tenantId, UUID venueId, UUID orderId, int course, String tableLabel, Priority priority,
                              List<FiredLine> lines) { }

    public record BoardEntry(UUID menuItemId, String reason, Instant since, Instant until) { }

    private final TicketRepository tickets;
    private final AvailabilityBoardRepository boards;
    private final StationDirectoryPort stations;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;

    public KitchenService(TicketRepository tickets, AvailabilityBoardRepository boards, StationDirectoryPort stations,
                          UnitOfWork uow, DomainEventSink events, DomainClock clock) {
        this.tickets = tickets;
        this.boards = boards;
        this.stations = stations;
        this.uow = uow;
        this.events = events;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- fire

    /**
     * Turns a fired course into one ticket per station. Idempotent: ticket ids are derived from
     * (order, course, station), so a redelivered "course fired" event collides with the first attempt, retries,
     * finds the existing tickets and returns them.
     */
    public Uni<List<TicketView>> fire(FireCommand cmd) {
        final var venue = VenueId.of(cmd.venueId());
        final var order = OrderId.of(cmd.orderId());
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() ->
                tickets.findByOrder(cmd.tenantId(), order).flatMap(existing -> {
                    final var already = existing.stream().filter(t -> t.course() == cmd.course()).toList();
                    if (!already.isEmpty()) {
                        return Uni.createFrom().item(views(already));
                    }
                    return createTickets(cmd, venue, order);
                })));
    }

    private Uni<List<TicketView>> createTickets(FireCommand cmd, VenueId venue, OrderId order) {
        final var now = clock.now();
        return tickets.findOnScreen(cmd.tenantId(), venue, Optional.empty()).flatMap(onScreen -> {
            final Map<StationId, Integer> load = new HashMap<>();
            onScreen.forEach(t -> load.merge(t.stationId(), t.activeItemCount(), Integer::sum));
            final var routed = cmd.lines().stream().map(l -> new TicketRouter.RoutedLine(
                    new TicketItem(l.lineId(), l.menuItemId(), l.name(), l.quantity(), l.modifiers(), l.notes(), l.allergens(),
                            Duration.ofSeconds(l.prepSeconds()), ItemState.QUEUED), StationId.of(l.stationId()))).toList();
            return resolveParallelism(cmd.tenantId(), venue, routed).flatMap(parallelism -> {
                final var drafts = TicketRouter.route(routed, now,
                        s -> new TicketRouter.StationLoad(load.getOrDefault(s, 0), parallelism.getOrDefault(s, 1)));
                final var created = new ArrayList<KitchenTicket>();
                Uni<Void> saves = Uni.createFrom().voidItem();
                for (final var d : drafts) {
                    final var ticket = KitchenTicket.create(TicketId.forOrderCourseStation(order, cmd.course(), d.station()), order, venue,
                            d.station(), cmd.course(), cmd.tableLabel(), cmd.priority(), d.items(), now, d.promisedAt());
                    created.add(ticket);
                    saves = saves.flatMap(x -> tickets.save(cmd.tenantId(), ticket));
                }
                return saves.flatMap(x -> publish(cmd.tenantId(), created)).replaceWith(views(created));
            });
        });
    }

    private Uni<Map<StationId, Integer>> resolveParallelism(UUID tenantId, VenueId venue, List<TicketRouter.RoutedLine> lines) {
        final var distinct = lines.stream().map(TicketRouter.RoutedLine::station).distinct().toList();
        final Map<StationId, Integer> out = new HashMap<>();
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (final var s : distinct) {
            chain = chain.flatMap(x -> stations.parallelism(tenantId, venue, s).invoke(p -> out.put(s, Math.max(1, p))).replaceWithVoid());
        }
        return chain.replaceWith(out);
    }

    // ---------------------------------------------------------------- KDS actions

    public Uni<TicketView> start(UUID tenantId, UUID ticketId, UUID lineId) {
        return mutate(tenantId, ticketId, t -> t.start(lineId, clock.now()));
    }

    public Uni<TicketView> startAll(UUID tenantId, UUID ticketId) {
        return mutate(tenantId, ticketId, t -> t.startAll(clock.now()));
    }

    public Uni<TicketView> markReady(UUID tenantId, UUID ticketId, UUID lineId) {
        return mutate(tenantId, ticketId, t -> t.markReady(lineId, clock.now()));
    }

    public Uni<TicketView> bump(UUID tenantId, UUID ticketId) {
        return mutate(tenantId, ticketId, t -> t.bump(clock.now()));
    }

    public Uni<TicketView> recall(UUID tenantId, UUID ticketId) {
        return mutate(tenantId, ticketId, t -> t.recall(clock.now()));
    }

    public Uni<TicketView> voidItem(UUID tenantId, UUID ticketId, UUID lineId, String reason) {
        return mutate(tenantId, ticketId, t -> t.voidItem(lineId, reason, clock.now()));
    }

    public Uni<TicketView> setPriority(UUID tenantId, UUID ticketId, Priority priority) {
        return mutate(tenantId, ticketId, t -> t.changePriority(priority, clock.now()));
    }

    /** Voids a line on whichever ticket of the order carries it (the order voided the line). Idempotent. */
    public Uni<Void> voidLineOfOrder(UUID tenantId, UUID orderId, UUID lineId, String reason) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> tickets.findByOrder(tenantId, OrderId.of(orderId)).flatMap(list -> {
            Uni<Void> chain = Uni.createFrom().voidItem();
            for (final var t : list) {
                if (t.items().stream().anyMatch(i -> i.lineId().equals(lineId)) && t.status().isOnScreen()) {
                    t.voidItem(lineId, reason, clock.now());
                    chain = chain.flatMap(x -> tickets.save(tenantId, t)).flatMap(x -> publish(tenantId, List.of(t)));
                }
            }
            return chain;
        })));
    }

    /** The KDS screen: highest priority first, then the earliest promise (most urgent). */
    public Uni<List<TicketView>> queue(UUID tenantId, UUID venueId, Optional<UUID> station) {
        final var now = clock.now();
        return tickets.findOnScreen(tenantId, VenueId.of(venueId), station.map(StationId::of)).map(list -> list.stream()
                .sorted(Comparator.comparingInt((KitchenTicket t) -> -t.priority().weight()).thenComparing(KitchenTicket::promisedAt))
                .map(t -> TicketView.of(t, now)).toList());
    }

    /** Count of tickets past their promise: exported as a gauge and alerted on. */
    public Uni<Long> lateCount(UUID tenantId, UUID venueId) {
        final var now = clock.now();
        return tickets.findOnScreen(tenantId, VenueId.of(venueId), Optional.empty())
                .map(list -> list.stream().filter(t -> t.isLate(now)).count());
    }

    // ---------------------------------------------------------------- 86 board

    public Uni<Void> eightySix(UUID tenantId, UUID venueId, UUID menuItemId, String reason, Instant until) {
        return board(tenantId, venueId, (b, now) -> b.eightySix(menuItemId, reason, until, now));
    }

    public Uni<Void> restore(UUID tenantId, UUID venueId, UUID menuItemId) {
        return board(tenantId, venueId, (b, now) -> b.restore(menuItemId, now));
    }

    public Uni<Boolean> isAvailable(UUID tenantId, UUID venueId, UUID menuItemId) {
        return boards.find(tenantId, VenueId.of(venueId)).map(o -> o.map(b -> b.isAvailable(menuItemId, clock.now())).orElse(true));
    }

    public Uni<List<BoardEntry>> unavailable(UUID tenantId, UUID venueId) {
        return boards.find(tenantId, VenueId.of(venueId)).map(o -> o.map(b -> b.unavailable(clock.now()).stream()
                .map(e -> new BoardEntry(e.menuItemId(), e.reason(), e.since(), e.until())).toList()).orElse(List.of()));
    }

    private Uni<Void> board(UUID tenantId, UUID venueId, java.util.function.BiConsumer<AvailabilityBoard, Instant> change) {
        final var venue = VenueId.of(venueId);
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> boards.find(tenantId, venue)
                .map(o -> o.orElseGet(() -> AvailabilityBoard.openFor(venue, clock.now()))).flatMap(b -> {
                    change.accept(b, clock.now());
                    final var raised = b.pullDomainEvents();
                    if (raised.isEmpty()) {
                        return Uni.createFrom().voidItem();
                    }
                    return boards.save(tenantId, b).flatMap(x -> events.publish(tenantId, raised));
                })));
    }

    // ---------------------------------------------------------------- helpers

    private Uni<TicketView> mutate(UUID tenantId, UUID ticketId, Consumer<KitchenTicket> action) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() ->
                tickets.find(tenantId, TicketId.of(ticketId)).map(o -> o.orElseThrow(() -> new NotFoundException("Ticket", ticketId)))
                        .flatMap(t -> {
                            action.accept(t);
                            return tickets.save(tenantId, t).flatMap(x -> publish(tenantId, List.of(t)))
                                    .replaceWith(TicketView.of(t, clock.now()));
                        })));
    }

    private Uni<Void> publish(UUID tenantId, List<KitchenTicket> changed) {
        final var all = new ArrayList<DomainEvent>();
        changed.forEach(t -> all.addAll(t.pullDomainEvents()));
        return all.isEmpty() ? Uni.createFrom().voidItem() : events.publish(tenantId, all);
    }

    private List<TicketView> views(List<KitchenTicket> list) {
        final var now = clock.now();
        return list.stream().map(t -> TicketView.of(t, now)).toList();
    }
}
