package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record RideNoDrivers(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID riderId) implements DomainEvent {

    public static RideNoDrivers at(Instant now, UUID rideId, UUID riderId) {
        return new RideNoDrivers(UUID.randomUUID(), now, rideId, riderId);
    }

    @Override
    public String eventType() {
        return "mobility.ride.no-drivers";
    }
}
