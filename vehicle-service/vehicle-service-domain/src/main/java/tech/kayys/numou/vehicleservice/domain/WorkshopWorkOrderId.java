package tech.kayys.numou.vehicleservice.domain;

import java.util.Objects;
import java.util.UUID;

public record WorkshopWorkOrderId(UUID value) {
    public WorkshopWorkOrderId {
        Objects.requireNonNull(value, "value");
    }

    public static WorkshopWorkOrderId generate() {
        return new WorkshopWorkOrderId(UUID.randomUUID());
    }
}
