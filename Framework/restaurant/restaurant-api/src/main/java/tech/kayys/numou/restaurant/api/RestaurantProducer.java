package tech.kayys.numou.restaurant.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.cache.CachePolicy;
import tech.kayys.numou.cache.adapter.CaffeineCache;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.observability.MetricsPort;
import tech.kayys.numou.reliability.adapter.json.JsonDomainEvent;
import tech.kayys.numou.reliability.adapter.json.EventTypeRegistry;
import tech.kayys.numou.reliability.spi.port.OutboxStorePort;
import tech.kayys.numou.restaurant.integration.VenueBackedPorts;
import tech.kayys.numou.restaurant.kernel.spi.DomainEventSink;
import tech.kayys.numou.restaurant.kitchen.application.KitchenService;
import tech.kayys.numou.restaurant.kitchen.spi.AvailabilityBoardRepository;
import tech.kayys.numou.restaurant.kitchen.spi.TicketRepository;
import tech.kayys.numou.restaurant.order.application.OrderService;
import tech.kayys.numou.restaurant.order.spi.OrderRepository;
import tech.kayys.numou.restaurant.persistence.MeteredDomainEventSink;
import tech.kayys.numou.restaurant.persistence.OutboxDomainEventSink;
import tech.kayys.numou.reservation.application.ReservationService;
import tech.kayys.numou.reservation.application.WaitlistService;
import tech.kayys.numou.reservation.domain.BookingRules;
import tech.kayys.numou.reservation.spi.LedgerRepository;
import tech.kayys.numou.reservation.spi.ReservationRepository;
import tech.kayys.numou.reservation.spi.WaitlistRepository;
import tech.kayys.numou.restaurant.venue.application.MenuView;
import tech.kayys.numou.restaurant.venue.application.VenueService;
import tech.kayys.numou.restaurant.venue.spi.DiningTableRepository;
import tech.kayys.numou.restaurant.venue.spi.MenuRepository;
import tech.kayys.numou.restaurant.venue.spi.VenueRepository;

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

    @ConfigProperty(name = "numou.restaurant.waitlist.average-turn", defaultValue = "PT60M")
    Duration averageTurn;
    @ConfigProperty(name = "numou.restaurant.waitlist.hold", defaultValue = "PT10M")
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

    /**
     * Bridges the reservation bounded-context event sink to the same outbox implementation.
     * Both interfaces declare the same method; the method reference satisfies the reservation.spi contract.
     */
    @Produces
    @ApplicationScoped
    tech.kayys.numou.reservation.spi.DomainEventSink reservationEventSink(DomainEventSink restaurantSink) {
        return restaurantSink::publish;
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
    VenueService venueService(VenueRepository v, DiningTableRepository t, MenuRepository m,
                              DomainEventSink e, DomainClock c, CaffeineCache<MenuView> cache) {
        return new VenueService(v, t, m, uow, e, c, cache);
    }

    @Produces
    @ApplicationScoped
    KitchenService kitchenService(TicketRepository t, AvailabilityBoardRepository b,
                                  DomainEventSink e, DomainClock c, VenueRepository venues) {
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
                                          tech.kayys.numou.reservation.spi.DomainEventSink e, DomainClock c) {
        return new ReservationService(r, l, VenueBackedPorts.bookingRules(tenant -> rules), VenueBackedPorts.tableDirectory(tables), uow, e, c);
    }

    @Produces
    @ApplicationScoped
    WaitlistService waitlistService(WaitlistRepository w, tech.kayys.numou.reservation.spi.DomainEventSink e, DomainClock c) {
        return new WaitlistService(w, uow, e, c, averageTurn, waitlistHold);
    }
}
