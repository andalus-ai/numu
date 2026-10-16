package tech.kayys.syirkah.restaurant.venue.spi;

import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kernel.spi.VersionedRepository;
import tech.kayys.syirkah.restaurant.venue.domain.Venue;

public interface VenueRepository extends VersionedRepository<Venue, VenueId> { }
