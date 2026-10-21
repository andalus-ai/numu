package tech.kayys.syirkah.restaurant.venue.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kernel.domain.DiningTableId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.aggregate.spi.VersionedRepository;
import tech.kayys.syirkah.restaurant.venue.domain.DiningTable;

import java.util.List;
import java.util.UUID;

public interface DiningTableRepository extends VersionedRepository<DiningTable, DiningTableId> {

    Uni<List<DiningTable>> findByVenue(UUID tenantId, VenueId venueId);
}
