package tech.kayys.syirkah.restaurant.order.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kernel.domain.OrderId;
import tech.kayys.syirkah.restaurant.order.domain.LineSpec;
import tech.kayys.syirkah.restaurant.order.domain.PricingPolicy;

import java.util.Optional;
import java.util.UUID;

/** Anti-corruption ports: how the order context sees the venue and kitchen contexts. */
public interface OrderPorts {

    /** The dish as currently priced on the venue's published menu; empty if absent, inactive or not published. */
    interface MenuLookup {
        Uni<Optional<LineSpec>> lineSpec(UUID tenantId, UUID venueId, UUID menuItemId);
    }

    /** Kitchen's 86 board. */
    interface ItemAvailability {
        Uni<Boolean> isAvailable(UUID tenantId, UUID venueId, UUID menuItemId);
    }

    interface VenuePolicy {
        Uni<PricingPolicy> pricingPolicy(UUID tenantId, UUID venueId);
    }

    /** Seats and releases physical tables. Runs in the caller's unit of work, so order + table change atomically. */
    interface TableSeating {
        /** @return the table's label */
        Uni<String> seat(UUID tenantId, UUID tableId, OrderId orderId, int covers);

        Uni<Void> release(UUID tenantId, UUID tableId);
    }
}
