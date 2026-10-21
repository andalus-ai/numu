package tech.kayys.syirkah.restaurant.reservation.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record ReservationNoShow(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID venueId,
        String outcome,
        BigDecimal chargeAmount,
        String currency) implements DomainEvent {

    public static ReservationNoShow at(Instant now, UUID reservationId, UUID venueId, String outcome, BigDecimal chargeAmount, String currency) {
        return new ReservationNoShow(UUID.randomUUID(), now, reservationId, venueId, outcome, chargeAmount, currency);
    }

    @Override
    public String eventType() {
        return "restaurant.reservation.no-show";
    }
}
