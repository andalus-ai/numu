package tech.kayys.syirkah.accounting.domain.maintenance;

import java.util.Objects;
import java.util.UUID;

public record MaintenancePlanId(String value) {
    public MaintenancePlanId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("MaintenancePlanId must not be blank");
    }
    public static MaintenancePlanId newId() {
        return new MaintenancePlanId(UUID.randomUUID().toString());
    }
    public static MaintenancePlanId of(String value) {
        return new MaintenancePlanId(value);
    }
}
