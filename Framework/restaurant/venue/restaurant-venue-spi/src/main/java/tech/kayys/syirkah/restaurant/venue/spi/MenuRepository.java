package tech.kayys.syirkah.restaurant.venue.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kernel.domain.MenuId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kernel.spi.VersionedRepository;
import tech.kayys.syirkah.restaurant.venue.domain.Menu;

import java.util.List;
import java.util.UUID;

public interface MenuRepository extends VersionedRepository<Menu, MenuId> {

    Uni<List<Menu>> findByVenue(UUID tenantId, VenueId venueId);
}
