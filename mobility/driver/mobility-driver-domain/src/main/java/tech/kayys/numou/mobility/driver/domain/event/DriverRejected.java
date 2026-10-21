package tech.kayys.numou.mobility.driver.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record DriverRejected(
        UUID eventId,
        Instant occurredAt,
        UUID driverId,
        String reason) implements DomainEvent {

    public static DriverRejected at(Instant now, UUID driverId, String reason) {
        return new DriverRejected(UUID.randomUUID(), now, driverId, reason);
    }

    @Override
    public String eventType() {
        return "mobility.driver.rejected";
    }
}
