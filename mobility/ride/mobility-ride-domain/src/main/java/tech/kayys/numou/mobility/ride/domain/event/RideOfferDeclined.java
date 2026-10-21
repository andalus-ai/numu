package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record RideOfferDeclined(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID driverId) implements DomainEvent {

    public static RideOfferDeclined at(Instant now, UUID rideId, UUID driverId) {
        return new RideOfferDeclined(UUID.randomUUID(), now, rideId, driverId);
    }

    @Override
    public String eventType() {
        return "mobility.ride.offer-declined";
    }
}
