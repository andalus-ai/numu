package tech.kayys.syirkah.restaurant.kitchen.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kernel.domain.StationId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;

import java.util.UUID;

/** How many items a station cooks at once (configuration owned by the venue context). */
public interface StationDirectoryPort {

    Uni<Integer> parallelism(UUID tenantId, VenueId venueId, StationId station);
}
