package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record RideRefunded(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID riderId,
        BigDecimal amount,
        BigDecimal totalRefunded,
        String currency) implements DomainEvent {

    public static RideRefunded at(Instant now, UUID rideId, UUID riderId, BigDecimal amount, BigDecimal totalRefunded, String currency) {
        return new RideRefunded(UUID.randomUUID(), now, rideId, riderId, amount, totalRefunded, currency);
    }

    @Override
    public String eventType() {
        return "mobility.ride.refunded";
    }
}
