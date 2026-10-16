package tech.kayys.numou.restaurant.venue.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.restaurant.kernel.domain.MenuId;
import tech.kayys.numou.restaurant.kernel.domain.VenueId;
import tech.kayys.numou.restaurant.kernel.spi.VersionedRepository;
import tech.kayys.numou.restaurant.venue.domain.Menu;

import java.util.List;
import java.util.UUID;

public interface MenuRepository extends VersionedRepository<Menu, MenuId> {

    Uni<List<Menu>> findByVenue(UUID tenantId, VenueId venueId);
}
