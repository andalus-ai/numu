package tech.kayys.syirkah.restaurant.kitchen.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kernel.domain.OrderId;
import tech.kayys.syirkah.restaurant.kernel.domain.StationId;
import tech.kayys.syirkah.restaurant.kernel.domain.TicketId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kernel.spi.VersionedRepository;
import tech.kayys.syirkah.restaurant.kitchen.domain.KitchenTicket;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends VersionedRepository<KitchenTicket, TicketId> {

    /** Tickets still on the KDS (new, in progress or ready), optionally for one station. */
    Uni<List<KitchenTicket>> findOnScreen(UUID tenantId, VenueId venueId, Optional<StationId> station);

    Uni<List<KitchenTicket>> findByOrder(UUID tenantId, OrderId orderId);
}
