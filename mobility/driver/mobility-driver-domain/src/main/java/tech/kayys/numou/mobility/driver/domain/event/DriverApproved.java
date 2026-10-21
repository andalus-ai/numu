package tech.kayys.numou.mobility.driver.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record DriverApproved(
        UUID eventId,
        Instant occurredAt,
        UUID driverId) implements DomainEvent {

    public static DriverApproved at(Instant now, UUID driverId) {
        return new DriverApproved(UUID.randomUUID(), now, driverId);
    }

    @Override
    public String eventType() {
        return "mobility.driver.approved";
    }
}
