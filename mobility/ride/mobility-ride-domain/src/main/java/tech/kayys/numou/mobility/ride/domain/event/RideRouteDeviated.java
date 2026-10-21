package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record RideRouteDeviated(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID riderId,
        UUID driverId,
        long deviationMeters) implements DomainEvent {

    public static RideRouteDeviated at(Instant now, UUID rideId, UUID riderId, UUID driverId, long deviationMeters) {
        return new RideRouteDeviated(UUID.randomUUID(), now, rideId, riderId, driverId, deviationMeters);
    }

    @Override
    public String eventType() {
        return "mobility.ride.route-deviated";
    }
}
