package tech.kayys.numou.restaurant.kitchen.adapter.memory;

import tech.kayys.numou.restaurant.kernel.domain.VenueId;
import tech.kayys.numou.restaurant.kernel.memory.InMemoryDocumentRepository;
import tech.kayys.numou.restaurant.kitchen.domain.AvailabilityBoard;
import tech.kayys.numou.restaurant.kitchen.spi.AvailabilityBoardRepository;

public class InMemoryAvailabilityBoardRepository extends InMemoryDocumentRepository<AvailabilityBoard, VenueId, AvailabilityBoard.Snapshot>
        implements AvailabilityBoardRepository {

    public InMemoryAvailabilityBoardRepository() {
        super(AvailabilityBoard::snapshot, AvailabilityBoard::restore);
    }
}
