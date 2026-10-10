package tech.kayys.syirkah.reservation.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.reservation.domain.FacilityId;
import tech.kayys.syirkah.reservation.domain.Reservation;
import tech.kayys.syirkah.reservation.domain.ReservationId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {
    Uni<Optional<Reservation>> find(UUID tenantId, ReservationId id);
    Uni<Void> save(UUID tenantId, Reservation reservation);
    Uni<List<Reservation>> findStartingBetween(UUID tenantId, FacilityId facilityId, Instant from, Instant to);
}
