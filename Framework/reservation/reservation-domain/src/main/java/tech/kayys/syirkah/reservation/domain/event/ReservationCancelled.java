package tech.kayys.syirkah.reservation.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationCancelled(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID facilityId,
        String charge,
        BigDecimal chargeAmount,
        String currency
) implements DomainEvent {

    public static ReservationCancelled at(Instant now, UUID reservationId, UUID facilityId, String charge, BigDecimal chargeAmount, String currency) {
        return new ReservationCancelled(UUID.randomUUID(), now, reservationId, facilityId, charge, chargeAmount, currency);
    }

    @Override
    public String eventType() {
        return "reservation.cancelled";
    }

    public UUID venueId() {
        return facilityId;
    }
}
