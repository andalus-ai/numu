package tech.kayys.numou.restaurant.integration;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.restaurant.kernel.domain.DiningTableId;
import tech.kayys.numou.restaurant.kernel.domain.MenuItemId;
import tech.kayys.numou.restaurant.kernel.domain.NotFoundException;
import tech.kayys.numou.restaurant.kernel.domain.OrderId;
import tech.kayys.numou.restaurant.kernel.domain.VenueId;
import tech.kayys.numou.restaurant.kernel.spi.DomainEventSink;
import tech.kayys.numou.restaurant.kitchen.application.KitchenService;
import tech.kayys.numou.restaurant.order.domain.LineSpec;
import tech.kayys.numou.restaurant.order.domain.OrderLine;
import tech.kayys.numou.restaurant.order.domain.PricingPolicy;
import tech.kayys.numou.restaurant.order.spi.OrderPorts;
import tech.kayys.numou.reservation.domain.BookableResource;
import tech.kayys.numou.reservation.domain.BookingRules;
import tech.kayys.numou.reservation.domain.FacilityId;
import tech.kayys.numou.reservation.domain.ResourceId;
import tech.kayys.numou.reservation.spi.BookingRulesPort;
import tech.kayys.numou.reservation.spi.ResourceDirectoryPort;
import tech.kayys.numou.restaurant.venue.domain.Menu;
import tech.kayys.numou.restaurant.venue.domain.TableStatus;
import tech.kayys.numou.restaurant.venue.spi.DiningTableRepository;
import tech.kayys.numou.restaurant.venue.spi.MenuRepository;
import tech.kayys.numou.restaurant.venue.spi.VenueRepository;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    public static ResourceDirectoryPort tableDirectory(DiningTableRepository tables) {
        return (tenantId, facilityId) -> tables.findByVenue(tenantId, VenueId.of(facilityId.value())).map(list -> list.stream()
                .filter(t -> t.status() != TableStatus.BLOCKED)
                .map(t -> new BookableResource(
                        ResourceId.of(t.id().value()),
                        t.label(),
                        t.minCovers(),
                        t.maxCovers(),
                        t.combinableWith().stream().map(id -> ResourceId.of(id.value())).collect(Collectors.toSet())
                )).toList());
    }

    /** Same rules for every venue; replace with a per-venue lookup when venues configure their own policy. */
    public static BookingRulesPort bookingRules(Function<UUID, BookingRules> byTenant) {
        return (tenantId, facilityId) -> Uni.createFrom().item(() -> byTenant.apply(tenantId));
    }
}
