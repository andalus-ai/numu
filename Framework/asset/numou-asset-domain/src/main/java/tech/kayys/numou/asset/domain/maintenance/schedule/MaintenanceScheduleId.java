package tech.kayys.syirkah.asset.domain.maintenance.schedule;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Identifier for {@link MaintenanceSchedule} (ASSET-22 §9). */
public record MaintenanceScheduleId(UUID value) implements DomainId<UUID>, Serializable {
        public MaintenanceScheduleId {
        Objects.requireNonNull(value, "MaintenanceScheduleId value cannot be null");
    }
    public static MaintenanceScheduleId of(UUID value) { return new MaintenanceScheduleId(value); }
    public static MaintenanceScheduleId generate() { return new MaintenanceScheduleId(UUID.randomUUID()); }
    public static MaintenanceScheduleId fromString(String value) { return new MaintenanceScheduleId(UUID.fromString(value)); }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
