package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record RideDriverWithdrew(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID driverId,
        int reassignments) implements DomainEvent {

    public static RideDriverWithdrew at(Instant now, UUID rideId, UUID driverId, int reassignments) {
        return new RideDriverWithdrew(UUID.randomUUID(), now, rideId, driverId, reassignments);
    }

    @Override
    public String eventType() {
        return "mobility.ride.driver-withdrew";
    }
}
