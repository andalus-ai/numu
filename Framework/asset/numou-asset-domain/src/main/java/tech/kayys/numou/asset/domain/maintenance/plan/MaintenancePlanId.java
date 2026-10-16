package tech.kayys.syirkah.asset.domain.maintenance.plan;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Identifier for {@link MaintenancePlan} (ASSET-22). */
public record MaintenancePlanId(UUID value) implements DomainId<UUID>, Serializable {
        public MaintenancePlanId {
        Objects.requireNonNull(value, "MaintenancePlanId value cannot be null");
    }
    public static MaintenancePlanId of(UUID value) { return new MaintenancePlanId(value); }
    public static MaintenancePlanId generate() { return new MaintenancePlanId(UUID.randomUUID()); }
    public static MaintenancePlanId fromString(String value) { return new MaintenancePlanId(UUID.fromString(value)); }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
