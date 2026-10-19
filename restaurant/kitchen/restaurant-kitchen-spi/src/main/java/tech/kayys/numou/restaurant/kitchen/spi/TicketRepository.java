package tech.kayys.numou.restaurant.kitchen.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.restaurant.kernel.domain.OrderId;
import tech.kayys.numou.restaurant.kernel.domain.StationId;
import tech.kayys.numou.restaurant.kernel.domain.TicketId;
import tech.kayys.numou.restaurant.kernel.domain.VenueId;
import tech.kayys.numou.restaurant.kernel.spi.VersionedRepository;
import tech.kayys.numou.restaurant.kitchen.domain.KitchenTicket;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends VersionedRepository<KitchenTicket, TicketId> {

    /** Tickets still on the KDS (new, in progress or ready), optionally for one station. */
    Uni<List<KitchenTicket>> findOnScreen(UUID tenantId, VenueId venueId, Optional<StationId> station);

    Uni<List<KitchenTicket>> findByOrder(UUID tenantId, OrderId orderId);
}
