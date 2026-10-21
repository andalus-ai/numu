package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record RideRated(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        String by,
        int stars) implements DomainEvent {

    public static RideRated at(Instant now, UUID rideId, String by, int stars) {
        return new RideRated(UUID.randomUUID(), now, rideId, by, stars);
    }

    @Override
    public String eventType() {
        return "mobility.ride.rated";
    }
}
