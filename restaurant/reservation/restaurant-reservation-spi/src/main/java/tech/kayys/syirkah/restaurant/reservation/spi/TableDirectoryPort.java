package tech.kayys.syirkah.restaurant.reservation.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.reservation.domain.TableInfo;

import java.util.List;
import java.util.UUID;

/** Anti-corruption port: the bookable tables of a venue, as the reservation context needs to see them. */
public interface TableDirectoryPort {

    Uni<List<TableInfo>> tablesFor(UUID tenantId, VenueId venueId);
}
