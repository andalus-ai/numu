package tech.kayys.syirkah.restaurant.reservation.adapter.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kernel.domain.ReservationId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.aggregate.memory.InMemoryDocumentRepository;
import tech.kayys.syirkah.restaurant.reservation.domain.Reservation;
import tech.kayys.syirkah.restaurant.reservation.spi.ReservationRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class InMemoryReservationRepository
        extends InMemoryDocumentRepository<Reservation, ReservationId, Reservation.Snapshot>
        implements ReservationRepository {

    public InMemoryReservationRepository() {
        super(Reservation::snapshot, Reservation::restore);
    }

    @Override
    public Uni<List<Reservation>> findStartingBetween(UUID tenantId, VenueId venueId, Instant from, Instant to) {
        return Uni.createFrom().item(() -> allOf(tenantId).stream()
                .filter(r -> r.venueId().equals(venueId) && !r.startsAt().isBefore(from) && r.startsAt().isBefore(to))
                .sorted(Comparator.comparing(Reservation::startsAt))
                .toList());
    }
}
