package tech.kayys.syirkah.restaurant.reservation.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kernel.domain.ReservationId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kernel.spi.VersionedRepository;
import tech.kayys.syirkah.restaurant.reservation.domain.Reservation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends VersionedRepository<Reservation, ReservationId> {

    /** Reservations whose start lies in {@code [from, to)}, earliest first. */
    Uni<List<Reservation>> findStartingBetween(UUID tenantId, VenueId venueId, Instant from, Instant to);
}
