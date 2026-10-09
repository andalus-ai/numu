package tech.kayys.syirkah.restaurant.kitchen.spi;

import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kernel.spi.VersionedRepository;
import tech.kayys.syirkah.restaurant.kitchen.domain.AvailabilityBoard;

public interface AvailabilityBoardRepository extends VersionedRepository<AvailabilityBoard, VenueId> { }
