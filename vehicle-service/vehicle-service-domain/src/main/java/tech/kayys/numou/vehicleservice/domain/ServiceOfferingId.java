package tech.kayys.numou.vehicleservice.domain;

import java.util.Objects;
import java.util.UUID;

public record ServiceOfferingId(UUID value) {
    public ServiceOfferingId {
        Objects.requireNonNull(value, "value");
    }

    public static ServiceOfferingId generate() {
        return new ServiceOfferingId(UUID.randomUUID());
    }
}
