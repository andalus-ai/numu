package tech.kayys.syirkah.restaurant.integration;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.restaurant.kernel.domain.DiningTableId;
import tech.kayys.syirkah.restaurant.kernel.domain.MenuItemId;
import tech.kayys.syirkah.aggregate.domain.NotFoundException;
import tech.kayys.syirkah.restaurant.kernel.domain.OrderId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.aggregate.spi.DomainEventSink;
import tech.kayys.syirkah.restaurant.kitchen.application.KitchenService;
import tech.kayys.syirkah.restaurant.order.domain.LineSpec;
import tech.kayys.syirkah.restaurant.order.domain.OrderLine;
import tech.kayys.syirkah.restaurant.order.domain.PricingPolicy;
import tech.kayys.syirkah.restaurant.order.spi.OrderPorts;
import tech.kayys.syirkah.restaurant.reservation.domain.BookingRules;
import tech.kayys.syirkah.restaurant.reservation.domain.TableInfo;
import tech.kayys.syirkah.restaurant.reservation.spi.BookingRulesPort;
import tech.kayys.syirkah.restaurant.reservation.spi.TableDirectoryPort;
import tech.kayys.syirkah.restaurant.venue.domain.Menu;
import tech.kayys.syirkah.restaurant.venue.domain.TableStatus;
import tech.kayys.syirkah.restaurant.venue.spi.DiningTableRepository;
import tech.kayys.syirkah.restaurant.venue.spi.MenuRepository;
import tech.kayys.syirkah.restaurant.venue.spi.VenueRepository;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Implements the other contexts' anti-corruption ports on top of the venue and kitchen contexts. Keeping these
 * in one integration module is what lets each context stay ignorant of the others.
 */
public final class VenueBackedPorts {

    private VenueBackedPorts() { }

    public static OrderPorts.MenuLookup menuLookup(MenuRepository menus) {
        return (tenantId, venueId, itemId) -> menus.findByVenue(tenantId, VenueId.of(venueId)).map(list -> list.stream()
                .filter(m -> m.status() == Menu.Status.PUBLISHED)
                .max(Comparator.comparing(m -> m.getUpdatedAt()))
                .flatMap(m -> m.find(MenuItemId.of(itemId)))
                .filter(i -> i.active())
                .map(i -> new LineSpec(i.id().value(), i.name(), i.price(), i.station().value(), i.prepTime().getSeconds(), i.allergens(),
                        i.modifiers().stream().map(x -> new OrderLine.Modifier(x.name(), x.priceDelta())).toList())));
    }

    public static OrderPorts.ItemAvailability availability(KitchenService kitchen) {
        return kitchen::isAvailable;
    }

    public static OrderPorts.VenuePolicy policy(VenueRepository venues) {
        return (tenantId, venueId) -> venues.find(tenantId, VenueId.of(venueId))
                .map(o -> o.orElseThrow(() -> new NotFoundException("Venue", venueId)))
                .map(v -> new PricingPolicy(v.currency(), v.fractionDigits(), v.taxRate(), v.serviceChargeRate(), v.taxOnServiceCharge()));
    }

    public static OrderPorts.TableSeating seating(DiningTableRepository tables, DomainEventSink events, DomainClock clock) {
        return new OrderPorts.TableSeating() {
            @Override
            public Uni<String> seat(UUID tenantId, UUID tableId, OrderId orderId, int covers) {
                return tables.find(tenantId, DiningTableId.of(tableId)).map(o -> o.orElseThrow(() -> new NotFoundException("Table", tableId)))
                        .flatMap(t -> {
                            t.seat(orderId, covers, clock.now());
                            return tables.save(tenantId, t).flatMap(x -> events.publish(tenantId, t.pullDomainEvents())).replaceWith(t.label());
                        });
            }

            @Override
            public Uni<Void> release(UUID tenantId, UUID tableId) {
                return tables.find(tenantId, DiningTableId.of(tableId)).map(o -> o.orElseThrow(() -> new NotFoundException("Table", tableId)))
                        .flatMap(t -> {
                            t.release(clock.now());
                            return tables.save(tenantId, t).flatMap(x -> events.publish(tenantId, t.pullDomainEvents()));
                        });
            }
        };
    }

    /** Bookable tables = every table that is not blocked (a broken table must not be sold). */
    public static TableDirectoryPort tableDirectory(DiningTableRepository tables) {
        return (tenantId, venueId) -> tables.findByVenue(tenantId, venueId).map(list -> list.stream()
                .filter(t -> t.status() != TableStatus.BLOCKED)
                .map(t -> new TableInfo(t.id(), t.label(), t.minCovers(), t.maxCovers(), t.combinableWith())).toList());
    }

    /** Same rules for every venue; replace with a per-venue lookup when venues configure their own policy. */
    public static BookingRulesPort bookingRules(Function<UUID, BookingRules> byTenant) {
        return (tenantId, venueId) -> Uni.createFrom().item(() -> byTenant.apply(tenantId));
    }
}
