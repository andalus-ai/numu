package tech.kayys.syirkah.restaurant.integration;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kitchen.application.KitchenService;
import tech.kayys.syirkah.restaurant.kitchen.domain.Priority;
import tech.kayys.syirkah.restaurant.kitchen.domain.event.TicketItemReady;
import tech.kayys.syirkah.restaurant.order.application.OrderService;
import tech.kayys.syirkah.restaurant.order.domain.event.CourseFired;
import tech.kayys.syirkah.restaurant.order.domain.event.OrderLineVoided;
import tech.kayys.syirkah.restaurant.reservation.application.WaitlistService;
import tech.kayys.syirkah.restaurant.venue.domain.event.TableCleaned;

import java.util.Set;

/**
 * Cross-context reactions. Each is idempotent (events arrive at-least-once) and only talks to the other context
 * through its application service.
 *
 * <ul>
 *   <li>order fired a course  -> kitchen creates tickets</li>
 *   <li>kitchen finished an item -> order marks the line ready</li>
 *   <li>order voided a line   -> kitchen voids it on the ticket</li>
 *   <li>a table was cleaned   -> the longest-waiting party that fits is notified</li>
 * </ul>
 */
public final class RestaurantPolicies {

    private RestaurantPolicies() { }

    public static void register(DispatchingEventSink bus, KitchenService kitchen, OrderService orders, WaitlistService waitlist) {
        bus.on(CourseFired.class, (tenant, e) -> kitchen.fire(new KitchenService.FireCommand(tenant, e.venueId(), e.orderId(), e.course(),
                e.tableLabel(), Priority.NORMAL, e.lines().stream().map(l -> new KitchenService.FiredLine(l.lineId(), l.menuItemId(), l.name(),
                l.quantity(), l.modifiers(), l.notes(), Set.copyOf(l.allergens()), l.prepSeconds(), l.stationId())).toList())).replaceWithVoid());

        bus.on(TicketItemReady.class, (tenant, e) -> orders.onLineReady(tenant, e.orderId(), e.lineId()));

        bus.on(OrderLineVoided.class, (tenant, e) -> kitchen.voidLineOfOrder(tenant, e.orderId(), e.lineId(), e.reason()));

        bus.on(TableCleaned.class, (tenant, e) -> waitlist.notifyNext(tenant, e.venueId(), e.maxCovers()).replaceWithVoid());
    }
}
