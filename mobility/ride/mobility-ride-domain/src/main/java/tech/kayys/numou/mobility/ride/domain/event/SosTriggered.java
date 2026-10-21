package tech.kayys.numou.mobility.ride.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record SosTriggered(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        String by) implements DomainEvent {

    public static SosTriggered at(Instant now, UUID rideId, String by) {
        return new SosTriggered(UUID.randomUUID(), now, rideId, by);
    }

    @Override
    public String eventType() {
        return "mobility.ride.sos";
    }
}
