package tech.kayys.numou.reservation.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationNoShow(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID facilityId,
        String charge,
        BigDecimal chargeAmount,
        String currency
) implements DomainEvent {

    public static ReservationNoShow at(Instant now, UUID reservationId, UUID facilityId, String charge, BigDecimal chargeAmount, String currency) {
        return new ReservationNoShow(UUID.randomUUID(), now, reservationId, facilityId, charge, chargeAmount, currency);
    }

    @Override
    public String eventType() {
        return "reservation.no-show";
    }

    public UUID venueId() {
        return facilityId;
    }
}
