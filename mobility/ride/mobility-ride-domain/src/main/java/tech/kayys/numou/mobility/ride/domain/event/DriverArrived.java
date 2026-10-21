package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record DriverArrived(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID riderId,
        UUID driverId) implements DomainEvent {

    public static DriverArrived at(Instant now, UUID rideId, UUID riderId, UUID driverId) {
        return new DriverArrived(UUID.randomUUID(), now, rideId, riderId, driverId);
    }

    @Override
    public String eventType() {
        return "mobility.ride.driver-arrived";
    }
}
