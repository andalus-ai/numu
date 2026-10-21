package tech.kayys.numou.mobility.driver.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record DriverWentOffline(
        UUID eventId,
        Instant occurredAt,
        UUID driverId) implements DomainEvent {

    public static DriverWentOffline at(Instant now, UUID driverId) {
        return new DriverWentOffline(UUID.randomUUID(), now, driverId);
    }

    @Override
    public String eventType() {
        return "mobility.driver.offline";
    }
}
