package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record RideAssigned(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID riderId,
        UUID driverId,
        long etaSeconds) implements DomainEvent {

    public static RideAssigned at(Instant now, UUID rideId, UUID riderId, UUID driverId, long etaSeconds) {
        return new RideAssigned(UUID.randomUUID(), now, rideId, riderId, driverId, etaSeconds);
    }

    @Override
    public String eventType() {
        return "mobility.ride.assigned";
    }
}
