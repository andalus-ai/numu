package tech.kayys.syirkah.restaurant.venue.adapter.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kernel.domain.DiningTableId;
import tech.kayys.syirkah.restaurant.kernel.domain.MenuId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.aggregate.memory.InMemoryDocumentRepository;
import tech.kayys.syirkah.restaurant.venue.domain.DiningTable;
import tech.kayys.syirkah.restaurant.venue.domain.Menu;
import tech.kayys.syirkah.restaurant.venue.domain.Venue;
import tech.kayys.syirkah.restaurant.venue.spi.DiningTableRepository;
import tech.kayys.syirkah.restaurant.venue.spi.MenuRepository;
import tech.kayys.syirkah.restaurant.venue.spi.VenueRepository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** In-memory venue-context repositories (tests / dev profile). */
public final class InMemoryVenueRepositories {

    private InMemoryVenueRepositories() { }

    public static class Venues extends InMemoryDocumentRepository<Venue, VenueId, Venue.Snapshot> implements VenueRepository {
        public Venues() { super(Venue::snapshot, Venue::restore); }
    }

    public static class Tables extends InMemoryDocumentRepository<DiningTable, DiningTableId, DiningTable.Snapshot> implements DiningTableRepository {
        public Tables() { super(DiningTable::snapshot, DiningTable::restore); }

        @Override
        public Uni<List<DiningTable>> findByVenue(UUID tenantId, VenueId venueId) {
            return Uni.createFrom().item(() -> allOf(tenantId).stream().filter(t -> t.venueId().equals(venueId))
                    .sorted(Comparator.comparing(DiningTable::label)).toList());
        }
    }

    public static class Menus extends InMemoryDocumentRepository<Menu, MenuId, Menu.Snapshot> implements MenuRepository {
        public Menus() { super(Menu::snapshot, Menu::restore); }

        @Override
        public Uni<List<Menu>> findByVenue(UUID tenantId, VenueId venueId) {
            return Uni.createFrom().item(() -> allOf(tenantId).stream().filter(m -> m.venueId().equals(venueId)).toList());
        }
    }
}
