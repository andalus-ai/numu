package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record RideStarted(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID riderId,
        UUID driverId) implements DomainEvent {

    public static RideStarted at(Instant now, UUID rideId, UUID riderId, UUID driverId) {
        return new RideStarted(UUID.randomUUID(), now, rideId, riderId, driverId);
    }

    @Override
    public String eventType() {
        return "mobility.ride.started";
    }
}
