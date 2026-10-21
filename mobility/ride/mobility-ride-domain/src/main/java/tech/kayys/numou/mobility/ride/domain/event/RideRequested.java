package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record RideRequested(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID riderId,
        String vehicleType,
        String pickupCell,
        String currency,
        BigDecimal estimatedTotal) implements DomainEvent {

    public static RideRequested at(Instant now, UUID rideId, UUID riderId, String vehicleType, String pickupCell, String currency, BigDecimal estimatedTotal) {
        return new RideRequested(UUID.randomUUID(), now, rideId, riderId, vehicleType, pickupCell, currency, estimatedTotal);
    }

    @Override
    public String eventType() {
        return "mobility.ride.requested";
    }
}
