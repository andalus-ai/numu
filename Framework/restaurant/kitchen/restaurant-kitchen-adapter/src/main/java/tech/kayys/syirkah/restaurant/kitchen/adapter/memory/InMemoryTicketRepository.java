package tech.kayys.syirkah.restaurant.kitchen.adapter.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kernel.domain.OrderId;
import tech.kayys.syirkah.restaurant.kernel.domain.StationId;
import tech.kayys.syirkah.restaurant.kernel.domain.TicketId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kernel.memory.InMemoryDocumentRepository;
import tech.kayys.syirkah.restaurant.kitchen.domain.KitchenTicket;
import tech.kayys.syirkah.restaurant.kitchen.spi.TicketRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class InMemoryTicketRepository extends InMemoryDocumentRepository<KitchenTicket, TicketId, KitchenTicket.Snapshot>
        implements TicketRepository {

    public InMemoryTicketRepository() {
        super(KitchenTicket::snapshot, KitchenTicket::restore);
    }

    @Override
    public Uni<List<KitchenTicket>> findOnScreen(UUID tenantId, VenueId venueId, Optional<StationId> station) {
        return Uni.createFrom().item(() -> allOf(tenantId).stream()
                .filter(t -> t.venueId().equals(venueId) && t.status().isOnScreen()
                        && station.map(s -> s.equals(t.stationId())).orElse(true)).toList());
    }

    @Override
    public Uni<List<KitchenTicket>> findByOrder(UUID tenantId, OrderId orderId) {
        return Uni.createFrom().item(() -> allOf(tenantId).stream().filter(t -> t.orderId().equals(orderId)).toList());
    }
}
