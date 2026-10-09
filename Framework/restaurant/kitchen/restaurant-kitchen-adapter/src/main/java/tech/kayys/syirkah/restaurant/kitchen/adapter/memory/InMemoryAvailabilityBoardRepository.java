package tech.kayys.syirkah.restaurant.kitchen.adapter.memory;

import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kernel.memory.InMemoryDocumentRepository;
import tech.kayys.syirkah.restaurant.kitchen.domain.AvailabilityBoard;
import tech.kayys.syirkah.restaurant.kitchen.spi.AvailabilityBoardRepository;

public class InMemoryAvailabilityBoardRepository extends InMemoryDocumentRepository<AvailabilityBoard, VenueId, AvailabilityBoard.Snapshot>
        implements AvailabilityBoardRepository {

    public InMemoryAvailabilityBoardRepository() {
        super(AvailabilityBoard::snapshot, AvailabilityBoard::restore);
    }
}
