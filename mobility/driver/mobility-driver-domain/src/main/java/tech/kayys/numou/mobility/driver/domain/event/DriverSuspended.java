package tech.kayys.numou.mobility.driver.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record DriverSuspended(
        UUID eventId,
        Instant occurredAt,
        UUID driverId,
        String reason,
        Instant until) implements DomainEvent {

    public static DriverSuspended at(Instant now, UUID driverId, String reason, Instant until) {
        return new DriverSuspended(UUID.randomUUID(), now, driverId, reason, until);
    }

    @Override
    public String eventType() {
        return "mobility.driver.suspended";
    }
}
