package tech.kayys.numou.restaurant.order.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.restaurant.kernel.domain.OrderId;
import tech.kayys.numou.restaurant.kernel.domain.VenueId;
import tech.kayys.numou.restaurant.kernel.spi.VersionedRepository;
import tech.kayys.numou.restaurant.order.domain.DineInOrder;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends VersionedRepository<DineInOrder, OrderId> {

    /** Orders still OPEN or awaiting payment, oldest first. */
    Uni<List<DineInOrder>> findActive(UUID tenantId, VenueId venueId);
}
