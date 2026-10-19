package tech.kayys.numou.restaurant.venue.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.restaurant.kernel.domain.DiningTableId;
import tech.kayys.numou.restaurant.kernel.domain.VenueId;
import tech.kayys.numou.restaurant.kernel.spi.VersionedRepository;
import tech.kayys.numou.restaurant.venue.domain.DiningTable;

import java.util.List;
import java.util.UUID;

public interface DiningTableRepository extends VersionedRepository<DiningTable, DiningTableId> {

    Uni<List<DiningTable>> findByVenue(UUID tenantId, VenueId venueId);
}
