package tech.kayys.syirkah.restaurant.reservation.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record ReservationCancelled(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID venueId,
        String outcome,
        BigDecimal chargeAmount,
        String currency) implements DomainEvent {

    public static ReservationCancelled at(Instant now, UUID reservationId, UUID venueId, String outcome, BigDecimal chargeAmount, String currency) {
        return new ReservationCancelled(UUID.randomUUID(), now, reservationId, venueId, outcome, chargeAmount, currency);
    }

    @Override
    public String eventType() {
        return "restaurant.reservation.cancelled";
    }
}
