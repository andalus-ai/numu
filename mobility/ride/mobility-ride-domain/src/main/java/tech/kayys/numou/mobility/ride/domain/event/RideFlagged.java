package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record RideFlagged(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID riderId,
        UUID driverId,
        int score,
        String reasons) implements DomainEvent {

    public static RideFlagged at(Instant now, UUID rideId, UUID riderId, UUID driverId, int score, String reasons) {
        return new RideFlagged(UUID.randomUUID(), now, rideId, riderId, driverId, score, reasons);
    }

    @Override
    public String eventType() {
        return "mobility.ride.flagged";
    }
}
