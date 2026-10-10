package tech.kayys.syirkah.restaurant.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.syirkah.restaurant.kernel.domain.*;
import tech.kayys.syirkah.restaurant.kitchen.domain.AvailabilityBoard;
import tech.kayys.syirkah.restaurant.kitchen.domain.KitchenTicket;
import tech.kayys.syirkah.restaurant.kitchen.spi.AvailabilityBoardRepository;
import tech.kayys.syirkah.restaurant.kitchen.spi.TicketRepository;
import tech.kayys.syirkah.restaurant.order.domain.DineInOrder;
import tech.kayys.syirkah.restaurant.order.spi.OrderRepository;
import tech.kayys.syirkah.reservation.domain.AvailabilityLedger;
import tech.kayys.syirkah.reservation.domain.FacilityId;
import tech.kayys.syirkah.reservation.domain.LedgerId;
import tech.kayys.syirkah.reservation.domain.Reservation;
import tech.kayys.syirkah.reservation.domain.ReservationId;
import tech.kayys.syirkah.reservation.domain.WaitlistEntry;
import tech.kayys.syirkah.reservation.domain.WaitlistEntryId;
import tech.kayys.syirkah.reservation.spi.LedgerRepository;
import tech.kayys.syirkah.reservation.spi.ReservationRepository;
import tech.kayys.syirkah.reservation.spi.WaitlistRepository;
import tech.kayys.syirkah.restaurant.venue.domain.DiningTable;
import tech.kayys.syirkah.restaurant.venue.domain.Menu;
import tech.kayys.syirkah.restaurant.venue.domain.Venue;
import tech.kayys.syirkah.restaurant.venue.spi.DiningTableRepository;
import tech.kayys.syirkah.restaurant.venue.spi.MenuRepository;
import tech.kayys.syirkah.restaurant.venue.spi.VenueRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Postgres repositories for every restaurant aggregate. Each is a few lines: kind + snapshot mapping + index columns. NOT COMPILED in sandbox. */
public final class PgRestaurantRepositories {

    private PgRestaurantRepositories() { }

    @ApplicationScoped
    public static class Reservations extends PgDocumentRepository<Reservation, ReservationId, Reservation.Snapshot> implements ReservationRepository {
        @Inject
        public Reservations(ObjectMapper m) {
            super(m, "reservation", Reservation.Snapshot.class, Reservation::snapshot, Reservation::restore,
                    r -> new Index(r.venueId().value(), null, r.startsAt(), r.status().name()));
        }

        @Override
        public Uni<List<Reservation>> findStartingBetween(UUID tenantId, FacilityId facilityId, Instant from, Instant to) {
            return query(tenantId, "venue_id = ?3 AND starts_at >= ?4 AND starts_at < ?5", "starts_at", facilityId.value(), from, to);
        }

        public Uni<List<Reservation>> findStartingBetween(UUID tenantId, VenueId venueId, Instant from, Instant to) {
            return findStartingBetween(tenantId, FacilityId.of(venueId.value()), from, to);
        }
    }

    @ApplicationScoped
    public static class Ledgers extends PgDocumentRepository<AvailabilityLedger, LedgerId, AvailabilityLedger.Snapshot> implements LedgerRepository {
        @Inject
        public Ledgers(ObjectMapper m) {
            super(m, "ledger", AvailabilityLedger.Snapshot.class, AvailabilityLedger::snapshot, AvailabilityLedger::restore,
                    l -> new Index(l.venueId().value(), null, l.day().atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), null));
        }
    }

    @ApplicationScoped
    public static class Waitlists extends PgDocumentRepository<WaitlistEntry, WaitlistEntryId, WaitlistEntry.Snapshot> implements WaitlistRepository {
        @Inject
        public Waitlists(ObjectMapper m) {
            super(m, "waitlist", WaitlistEntry.Snapshot.class, WaitlistEntry::snapshot, WaitlistEntry::restore,
                    w -> new Index(w.venueId().value(), null, w.joinedAt(), w.status().name()));
        }

        @Override
        public Uni<List<WaitlistEntry>> findOpen(UUID tenantId, FacilityId facilityId) {
            return query(tenantId, "venue_id = ?3 AND status IN ('WAITING','NOTIFIED')", "starts_at", facilityId.value());
        }

        public Uni<List<WaitlistEntry>> findOpen(UUID tenantId, VenueId venueId) {
            return findOpen(tenantId, FacilityId.of(venueId.value()));
        }
    }

    @ApplicationScoped
    public static class Tickets extends PgDocumentRepository<KitchenTicket, TicketId, KitchenTicket.Snapshot> implements TicketRepository {
        @Inject
        public Tickets(ObjectMapper m) {
            super(m, "ticket", KitchenTicket.Snapshot.class, KitchenTicket::snapshot, KitchenTicket::restore,
                    t -> new Index(t.venueId().value(), t.orderId().value(), t.firedAt(), t.status().name()));
        }

        @Override
        public Uni<List<KitchenTicket>> findOnScreen(UUID tenantId, VenueId venueId, Optional<StationId> station) {
            final String base = "venue_id = ?3 AND status IN ('NEW','IN_PROGRESS','READY')";
            return station.isPresent()
                    ? query(tenantId, base + " AND doc->>'stationId' = ?4", "starts_at", venueId.value(), station.get().value().toString())
                    : query(tenantId, base, "starts_at", venueId.value());
        }

        @Override
        public Uni<List<KitchenTicket>> findByOrder(UUID tenantId, OrderId orderId) {
            return query(tenantId, "ref_id = ?3", "starts_at", orderId.value());
        }
    }

    @ApplicationScoped
    public static class Boards extends PgDocumentRepository<AvailabilityBoard, VenueId, AvailabilityBoard.Snapshot> implements AvailabilityBoardRepository {
        @Inject
        public Boards(ObjectMapper m) {
            super(m, "board", AvailabilityBoard.Snapshot.class, AvailabilityBoard::snapshot, AvailabilityBoard::restore,
                    b -> new Index(b.id().value(), null, null, null));
        }
    }

    @ApplicationScoped
    public static class Orders extends PgDocumentRepository<DineInOrder, OrderId, DineInOrder.Snapshot> implements OrderRepository {
        @Inject
        public Orders(ObjectMapper m) {
            super(m, "order", DineInOrder.Snapshot.class, DineInOrder::snapshot, DineInOrder::restore,
                    o -> new Index(o.venueId().value(), null, o.getCreatedAt(), o.status().name()));
        }

        @Override
        public Uni<List<DineInOrder>> findActive(UUID tenantId, VenueId venueId) {
            return query(tenantId, "venue_id = ?3 AND status IN ('OPEN','BILL_REQUESTED')", "starts_at", venueId.value());
        }
    }

    @ApplicationScoped
    public static class Venues extends PgDocumentRepository<Venue, VenueId, Venue.Snapshot> implements VenueRepository {
        @Inject
        public Venues(ObjectMapper m) {
            super(m, "venue", Venue.Snapshot.class, Venue::snapshot, Venue::restore, v -> new Index(v.id().value(), null, null, null));
        }
    }

    @ApplicationScoped
    public static class Tables extends PgDocumentRepository<DiningTable, DiningTableId, DiningTable.Snapshot> implements DiningTableRepository {
        @Inject
        public Tables(ObjectMapper m) {
            super(m, "table", DiningTable.Snapshot.class, DiningTable::snapshot, DiningTable::restore,
                    t -> new Index(t.venueId().value(), null, null, t.status().name()));
        }

        @Override
        public Uni<List<DiningTable>> findByVenue(UUID tenantId, VenueId venueId) {
            return query(tenantId, "venue_id = ?3", "doc->>'label'", venueId.value());
        }
    }

    @ApplicationScoped
    public static class Menus extends PgDocumentRepository<Menu, MenuId, Menu.Snapshot> implements MenuRepository {
        @Inject
        public Menus(ObjectMapper m) {
            super(m, "menu", Menu.Snapshot.class, Menu::snapshot, Menu::restore, x -> new Index(x.venueId().value(), null, null, x.status().name()));
        }

        @Override
        public Uni<List<Menu>> findByVenue(UUID tenantId, VenueId venueId) {
            return query(tenantId, "venue_id = ?3", "updated_at", venueId.value());
        }
    }
}
