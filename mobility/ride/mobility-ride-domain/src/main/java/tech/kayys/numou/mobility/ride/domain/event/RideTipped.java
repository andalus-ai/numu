package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record RideTipped(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID riderId,
        UUID driverId,
        BigDecimal amount,
        String currency) implements DomainEvent {

    public static RideTipped at(Instant now, UUID rideId, UUID riderId, UUID driverId, BigDecimal amount, String currency) {
        return new RideTipped(UUID.randomUUID(), now, rideId, riderId, driverId, amount, currency);
    }

    @Override
    public String eventType() {
        return "mobility.ride.tipped";
    }
}
