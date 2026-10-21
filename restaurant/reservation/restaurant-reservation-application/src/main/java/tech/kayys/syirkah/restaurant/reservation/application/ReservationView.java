package tech.kayys.syirkah.restaurant.reservation.application;

import tech.kayys.syirkah.restaurant.reservation.domain.Reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read model returned to callers. Contains no more personal data than the caller already supplied (name only). */
public record ReservationView(UUID id, UUID venueId, String guestName, int partySize, Instant startsAt, String status,
                              List<UUID> tableIds, BigDecimal depositAmount, String depositCurrency, boolean depositPaid) {

    public static ReservationView of(Reservation r) {
        return new ReservationView(r.id().value(), r.venueId().value(), r.guest().name(), r.party().value(), r.startsAt(),
                r.status().name(), r.tableIds().stream().map(t -> t.value()).sorted().toList(),
                r.deposit() == null ? null : r.deposit().amount(), r.deposit() == null ? null : r.deposit().currency().code(),
                r.depositPaid());
    }
}
