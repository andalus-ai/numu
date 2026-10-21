package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record RideCancelled(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID riderId,
        UUID driverId,
        String cancelledBy,
        BigDecimal fee,
        String currency,
        boolean hadDriver) implements DomainEvent {

    public static RideCancelled at(Instant now, UUID rideId, UUID riderId, UUID driverId, String cancelledBy, BigDecimal fee, String currency, boolean hadDriver) {
        return new RideCancelled(UUID.randomUUID(), now, rideId, riderId, driverId, cancelledBy, fee, currency, hadDriver);
    }

    @Override
    public String eventType() {
        return "mobility.ride.cancelled";
    }
}
