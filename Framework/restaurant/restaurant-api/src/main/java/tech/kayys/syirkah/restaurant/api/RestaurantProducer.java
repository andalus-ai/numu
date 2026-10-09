package tech.kayys.syirkah.restaurant.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.syirkah.cache.CachePolicy;
import tech.kayys.syirkah.cache.adapter.CaffeineCache;
import tech.kayys.syirkah.foundation.adapter.context.RequestContextHolder;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;
import tech.kayys.syirkah.observability.MetricsPort;
import tech.kayys.syirkah.reliability.adapter.json.JsonDomainEvent;
import tech.kayys.syirkah.reliability.adapter.json.EventTypeRegistry;
import tech.kayys.syirkah.reliability.spi.port.OutboxStorePort;
import tech.kayys.syirkah.restaurant.integration.VenueBackedPorts;
import tech.kayys.syirkah.restaurant.kernel.spi.DomainEventSink;
import tech.kayys.syirkah.restaurant.kitchen.application.KitchenService;
import tech.kayys.syirkah.restaurant.kitchen.spi.AvailabilityBoardRepository;
import tech.kayys.syirkah.restaurant.kitchen.spi.TicketRepository;
import tech.kayys.syirkah.restaurant.order.application.OrderService;
import tech.kayys.syirkah.restaurant.order.spi.OrderRepository;
import tech.kayys.syirkah.restaurant.persistence.MeteredDomainEventSink;
import tech.kayys.syirkah.restaurant.persistence.OutboxDomainEventSink;
import tech.kayys.syirkah.restaurant.reservation.application.ReservationService;
import tech.kayys.syirkah.restaurant.reservation.application.WaitlistService;
import tech.kayys.syirkah.restaurant.reservation.domain.BookingRules;
import tech.kayys.syirkah.restaurant.reservation.spi.LedgerRepository;
import tech.kayys.syirkah.restaurant.reservation.spi.ReservationRepository;
import tech.kayys.syirkah.restaurant.reservation.spi.WaitlistRepository;
import tech.kayys.syirkah.restaurant.venue.application.MenuView;
import tech.kayys.syirkah.restaurant.venue.application.VenueService;
import tech.kayys.syirkah.restaurant.venue.spi.DiningTableRepository;
import tech.kayys.syirkah.restaurant.venue.spi.MenuRepository;
import tech.kayys.syirkah.restaurant.venue.spi.VenueRepository;

import java.time.Duration;
import java.time.Instant;

/**
 * Composition root of the restaurant suite for the Quarkus runtime: repositories are Postgres-backed (see
 * restaurant-persistence), events go to the transactional outbox, and the cross-context ports are implemented over
 * the venue/kitchen contexts. Policies that react to events (kitchen on "course fired", etc.) run as outbox
 * consumers in production; see docs/PRODUCTION.md. NOT COMPILED in the authoring sandbox.
 */
@ApplicationScoped
public class RestaurantProducer {

    @Inject
    ObjectMapper mapper;
    @Inject
    UnitOfWork uow;
    @Inject
    OutboxStorePort outbox;
    @Inject
    MetricsPort metrics;
    @Inject
    Instance<RequestContextHolder> requestContext;
    @Inject
    EventTypeRegistry eventTypes;

    @ConfigProperty(name = "syirkah.restaurant.waitlist.average-turn", defaultValue = "PT60M")
    Duration averageTurn;
    @ConfigProperty(name = "syirkah.restaurant.waitlist.hold", defaultValue = "PT10M")
    Duration waitlistHold;

    @Produces
    @ApplicationScoped
    DomainClock clock() {
        return Instant::now;
    }

    @Produces
    @ApplicationScoped
    DomainEventSink eventSink() {
        eventTypes.registerFallback(JsonDomainEvent.class);
        return new MeteredDomainEventSink(new OutboxDomainEventSink(outbox, mapper, this::actor, this::correlationId), metrics);
    }

    private String actor() {
        try {
            return requestContext.get().actor() == null ? "system" : requestContext.get().actor();
        } catch (RuntimeException notInRequest) {
            return "system";
        }
    }

    private String correlationId() {
        try {
            return requestContext.get().correlationId() == null ? java.util.UUID.randomUUID().toString() : requestContext.get().correlationId();
        } catch (RuntimeException notInRequest) {
            return java.util.UUID.randomUUID().toString();
        }
    }

    @Produces
    @ApplicationScoped
    CaffeineCache<MenuView> menuCache() {
        return new CaffeineCache<>(VenueService.MENU_CACHE, new CachePolicy(Duration.ofMinutes(5), Duration.ofSeconds(15), 10_000));
    }

    @Produces
    @ApplicationScoped
    VenueService venueService(VenueRepository v, DiningTableRepository t, MenuRepository m, DomainEventSink e, DomainClock c, CaffeineCache<MenuView> cache) {
        return new VenueService(v, t, m, uow, e, c, cache);
    }

    @Produces
    @ApplicationScoped
    KitchenService kitchenService(TicketRepository t, AvailabilityBoardRepository b, DomainEventSink e, DomainClock c, VenueRepository venues) {
        return new KitchenService(t, b, (tenant, venue, station) -> venues.find(tenant, venue)
                .map(o -> o.flatMap(v -> v.stations().stream().filter(s -> s.id().equals(station)).findFirst()).map(s -> s.parallelism()).orElse(1)),
                uow, e, c);
    }

    @Produces
    @ApplicationScoped
    OrderService orderService(OrderRepository o, MenuRepository menus, KitchenService kitchen, VenueRepository venues, DiningTableRepository tables,
                              DomainEventSink e, DomainClock c) {
        return new OrderService(o, VenueBackedPorts.menuLookup(menus), VenueBackedPorts.availability(kitchen), VenueBackedPorts.policy(venues),
                VenueBackedPorts.seating(tables, e, c), uow, e, c);
    }

    @Produces
    @ApplicationScoped
    ReservationService reservationService(ReservationRepository r, LedgerRepository l, DiningTableRepository tables, BookingRules rules,
                                          DomainEventSink e, DomainClock c) {
        return new ReservationService(r, l, VenueBackedPorts.bookingRules(tenant -> rules), VenueBackedPorts.tableDirectory(tables), uow, e, c);
    }

    @Produces
    @ApplicationScoped
    WaitlistService waitlistService(WaitlistRepository w, DomainEventSink e, DomainClock c) {
        return new WaitlistService(w, uow, e, c, averageTurn, waitlistHold);
    }
}
