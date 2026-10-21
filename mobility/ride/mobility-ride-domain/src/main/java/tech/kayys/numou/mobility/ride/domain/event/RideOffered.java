package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record RideOffered(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID driverId,
        int offerNumber) implements DomainEvent {

    public static RideOffered at(Instant now, UUID rideId, UUID driverId, int offerNumber) {
        return new RideOffered(UUID.randomUUID(), now, rideId, driverId, offerNumber);
    }

    @Override
    public String eventType() {
        return "mobility.ride.offered";
    }
}
