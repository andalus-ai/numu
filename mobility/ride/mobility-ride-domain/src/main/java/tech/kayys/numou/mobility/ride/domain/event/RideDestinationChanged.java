package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record RideDestinationChanged(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        BigDecimal newTotal) implements DomainEvent {

    public static RideDestinationChanged at(Instant now, UUID rideId, BigDecimal newTotal) {
        return new RideDestinationChanged(UUID.randomUUID(), now, rideId, newTotal);
    }

    @Override
    public String eventType() {
        return "mobility.ride.destination-changed";
    }
}
