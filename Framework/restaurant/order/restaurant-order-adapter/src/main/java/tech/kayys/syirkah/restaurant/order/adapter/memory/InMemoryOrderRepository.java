package tech.kayys.syirkah.restaurant.order.adapter.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kernel.domain.OrderId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kernel.memory.InMemoryDocumentRepository;
import tech.kayys.syirkah.restaurant.order.domain.DineInOrder;
import tech.kayys.syirkah.restaurant.order.domain.OrderStatus;
import tech.kayys.syirkah.restaurant.order.spi.OrderRepository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class InMemoryOrderRepository extends InMemoryDocumentRepository<DineInOrder, OrderId, DineInOrder.Snapshot> implements OrderRepository {

    public InMemoryOrderRepository() {
        super(DineInOrder::snapshot, DineInOrder::restore);
    }

    @Override
    public Uni<List<DineInOrder>> findActive(UUID tenantId, VenueId venueId) {
        return Uni.createFrom().item(() -> allOf(tenantId).stream()
                .filter(o -> o.venueId().equals(venueId) && (o.status() == OrderStatus.OPEN || o.status() == OrderStatus.BILL_REQUESTED))
                .sorted(Comparator.comparing(DineInOrder::getCreatedAt)).toList());
    }
}
