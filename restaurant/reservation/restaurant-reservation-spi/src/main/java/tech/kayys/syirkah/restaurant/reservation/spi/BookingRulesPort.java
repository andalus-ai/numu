package tech.kayys.syirkah.restaurant.reservation.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.reservation.domain.BookingRules;

import java.util.UUID;

/** Anti-corruption port: where a venue's booking policy comes from (venue configuration). */
public interface BookingRulesPort {

    Uni<BookingRules> rulesFor(UUID tenantId, VenueId venueId);
}
