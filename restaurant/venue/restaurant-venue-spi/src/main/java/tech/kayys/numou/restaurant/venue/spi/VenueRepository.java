package tech.kayys.numou.restaurant.venue.spi;

import tech.kayys.numou.restaurant.kernel.domain.VenueId;
import tech.kayys.numou.restaurant.kernel.spi.VersionedRepository;
import tech.kayys.numou.restaurant.venue.domain.Venue;

public interface VenueRepository extends VersionedRepository<Venue, VenueId> { }
