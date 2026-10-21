package tech.kayys.syirkah.restaurant.reservation.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

public record ReservationSeated(
        UUID eventId,
        Instant occurredAt,
        UUID reservationId,
        UUID venueId,
        List<UUID> tableIds) implements DomainEvent {

    public static ReservationSeated at(Instant now, UUID reservationId, UUID venueId, List<UUID> tableIds) {
        return new ReservationSeated(UUID.randomUUID(), now, reservationId, venueId, tableIds);
    }

    @Override
    public String eventType() {
        return "restaurant.reservation.seated";
    }
}
