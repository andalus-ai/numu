package tech.kayys.numou.restaurant.kitchen.spi;

import tech.kayys.numou.restaurant.kernel.domain.VenueId;
import tech.kayys.numou.restaurant.kernel.spi.VersionedRepository;
import tech.kayys.numou.restaurant.kitchen.domain.AvailabilityBoard;

public interface AvailabilityBoardRepository extends VersionedRepository<AvailabilityBoard, VenueId> { }
