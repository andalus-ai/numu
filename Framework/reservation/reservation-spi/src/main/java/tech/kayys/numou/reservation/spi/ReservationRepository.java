package tech.kayys.numou.reservation.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.reservation.domain.FacilityId;
import tech.kayys.numou.reservation.domain.Reservation;
import tech.kayys.numou.reservation.domain.ReservationId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {
    Uni<Optional<Reservation>> find(UUID tenantId, ReservationId id);
    Uni<Void> save(UUID tenantId, Reservation reservation);
    Uni<List<Reservation>> findStartingBetween(UUID tenantId, FacilityId facilityId, Instant from, Instant to);
}
