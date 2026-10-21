package tech.kayys.numou.mobility.driver.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record DriverWentOnline(
        UUID eventId,
        Instant occurredAt,
        UUID driverId,
        String vehicleType) implements DomainEvent {

    public static DriverWentOnline at(Instant now, UUID driverId, String vehicleType) {
        return new DriverWentOnline(UUID.randomUUID(), now, driverId, vehicleType);
    }

    @Override
    public String eventType() {
        return "mobility.driver.online";
    }
}
