package tech.kayys.syirkah.restaurant.order.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;
import tech.kayys.syirkah.restaurant.kernel.application.ConflictRetry;
import tech.kayys.syirkah.restaurant.kernel.domain.DiningTableId;
import tech.kayys.syirkah.restaurant.kernel.domain.NotFoundException;
import tech.kayys.syirkah.restaurant.kernel.domain.OrderId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kernel.spi.DomainEventSink;
import tech.kayys.syirkah.restaurant.order.domain.DineInOrder;
import tech.kayys.syirkah.restaurant.order.domain.OrderStatus;
import tech.kayys.syirkah.restaurant.order.spi.OrderPorts;
import tech.kayys.syirkah.restaurant.order.spi.OrderRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Dine-in order use cases. Every mutation is one unit of work (aggregate + events + table changes commit
 * together) retried on optimistic-lock conflicts. Approvals ({@code approvedBy}) are verified by the caller's
 * permission check; this service only records them.
 */
public final class OrderService {

    private static final int MAX_ATTEMPTS = 3;

    private final OrderRepository orders;
    private final OrderPorts.MenuLookup menu;
    private final OrderPorts.ItemAvailability availability;
    private final OrderPorts.VenuePolicy policies;
    private final OrderPorts.TableSeating seating;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;

    public OrderService(OrderRepository orders, OrderPorts.MenuLookup menu, OrderPorts.ItemAvailability availability,
                        OrderPorts.VenuePolicy policies, OrderPorts.TableSeating seating, UnitOfWork uow,
                        DomainEventSink events, DomainClock clock) {
        this.orders = orders;
        this.menu = menu;
        this.availability = availability;
        this.policies = policies;
        this.seating = seating;
        this.uow = uow;
        this.events = events;
        this.clock = clock;
    }

    public Uni<OrderView> open(UUID tenantId, UUID venueId, Set<UUID> tableIds, String server, int covers) {
        final var id = OrderId.generate();
        return uow.execute(() -> policies.pricingPolicy(tenantId, venueId).flatMap(policy -> {
            final var labels = new ArrayList<String>();
            Uni<Void> seated = Uni.createFrom().voidItem();
            for (final UUID t : tableIds) {
                seated = seated.flatMap(x -> seating.seat(tenantId, t, id, covers).invoke(labels::add).replaceWithVoid());
            }
            return seated.flatMap(x -> {
                final var order = DineInOrder.open(id, VenueId.of(venueId),
                        tableIds.stream().map(DiningTableId::of).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)),
                        String.join("+", labels.stream().sorted().toList()), server, covers, policy, clock.now());
                return save(tenantId, order).replaceWith(OrderView.of(order));
            });
        }));
    }

    public Uni<OrderView> addItem(UUID tenantId, UUID orderId, UUID menuItemId, int quantity, List<String> modifiers, String notes, int course) {
        return change(tenantId, orderId, order ->
                menu.lineSpec(tenantId, order.venueId().value(), menuItemId).flatMap(spec -> {
                    if (spec.isEmpty()) {
                        return Uni.<Void>createFrom().failure(new NotFoundException("Menu item", menuItemId));
                    }
                    return availability.isAvailable(tenantId, order.venueId().value(), menuItemId).flatMap(ok -> {
                        if (!ok) {
                            return Uni.<Void>createFrom().failure(new BusinessRuleViolation("'" + spec.get().name() + "' is sold out (86'd)"));
                        }
                        order.addLine(spec.get(), quantity, modifiers, notes, course, clock.now());
                        return Uni.createFrom().voidItem();
                    });
                }));
    }

    public Uni<OrderView> changeQuantity(UUID tenantId, UUID orderId, UUID lineId, int quantity) {
        return sync(tenantId, orderId, o -> o.changeQuantity(lineId, quantity, clock.now()));
    }

    public Uni<OrderView> removePendingLine(UUID tenantId, UUID orderId, UUID lineId) {
        return sync(tenantId, orderId, o -> o.removePendingLine(lineId, clock.now()));
    }

    public Uni<OrderView> fire(UUID tenantId, UUID orderId, int course) {
        return sync(tenantId, orderId, o -> o.fireCourse(course, clock.now()));
    }

    public Uni<OrderView> voidLine(UUID tenantId, UUID orderId, UUID lineId, String reason, String approvedBy) {
        return sync(tenantId, orderId, o -> o.voidLine(lineId, reason, approvedBy, clock.now()));
    }

    public Uni<OrderView> applyDiscount(UUID tenantId, UUID orderId, BigDecimal percent, String approvedBy) {
        return sync(tenantId, orderId, o -> o.applyDiscount(percent, approvedBy, clock.now()));
    }

    public Uni<OrderView> requestBill(UUID tenantId, UUID orderId) {
        return sync(tenantId, orderId, o -> o.requestBill(clock.now()));
    }

    public Uni<OrderView> reopenBill(UUID tenantId, UUID orderId) {
        return sync(tenantId, orderId, o -> o.reopen(clock.now()));
    }

    /** Settles the bill and frees the tables (to DIRTY) in the same unit of work. */
    public Uni<OrderView> settle(UUID tenantId, UUID orderId, String paymentRef) {
        return change(tenantId, orderId, o -> {
            o.settle(paymentRef, clock.now());
            return releaseTables(tenantId, o);
        });
    }

    public Uni<OrderView> voidOrder(UUID tenantId, UUID orderId, String reason, String approvedBy) {
        return change(tenantId, orderId, o -> {
            o.voidOrder(reason, approvedBy, clock.now());
            return releaseTables(tenantId, o);
        });
    }

    // ---- kitchen callbacks (idempotent: events arrive at-least-once)

    public Uni<Void> onLineReady(UUID tenantId, UUID orderId, UUID lineId) {
        return sync(tenantId, orderId, o -> o.markLineReady(lineId, clock.now())).replaceWithVoid();
    }

    public Uni<OrderView> markServed(UUID tenantId, UUID orderId, UUID lineId) {
        return sync(tenantId, orderId, o -> o.markLineServed(lineId, clock.now()));
    }

    // ---- reads

    public Uni<OrderView> get(UUID tenantId, UUID orderId) {
        return load(tenantId, orderId).map(OrderView::of);
    }

    public Uni<List<BigDecimal>> splitBill(UUID tenantId, UUID orderId, int ways) {
        return load(tenantId, orderId).map(o -> o.totals().splitEvenly(ways, o.policy().fractionDigits()).stream().map(m -> m.amount()).toList());
    }

    public Uni<List<OrderView>> active(UUID tenantId, UUID venueId) {
        return orders.findActive(tenantId, VenueId.of(venueId)).map(l -> l.stream().map(OrderView::of).toList());
    }

    // ---- internals

    private Uni<Void> releaseTables(UUID tenantId, DineInOrder o) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (final var t : o.tableIds()) {
            chain = chain.flatMap(x -> seating.release(tenantId, t.value()));
        }
        return chain;
    }

    private Uni<OrderView> sync(UUID tenantId, UUID orderId, Consumer<DineInOrder> action) {
        return change(tenantId, orderId, o -> {
            action.accept(o);
            return Uni.createFrom().voidItem();
        });
    }

    private Uni<OrderView> change(UUID tenantId, UUID orderId, java.util.function.Function<DineInOrder, Uni<Void>> action) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> load(tenantId, orderId).flatMap(o ->
                action.apply(o).flatMap(x -> save(tenantId, o)).map(x -> OrderView.of(o)))));
    }

    private Uni<Void> save(UUID tenantId, DineInOrder o) {
        return orders.save(tenantId, o).flatMap(x -> {
            final var raised = o.pullDomainEvents();
            return raised.isEmpty() ? Uni.createFrom().voidItem() : events.publish(tenantId, raised);
        });
    }

    private Uni<DineInOrder> load(UUID tenantId, UUID orderId) {
        return orders.find(tenantId, OrderId.of(orderId)).map(o -> o.orElseThrow(() -> new NotFoundException("Order", orderId)));
    }
}
