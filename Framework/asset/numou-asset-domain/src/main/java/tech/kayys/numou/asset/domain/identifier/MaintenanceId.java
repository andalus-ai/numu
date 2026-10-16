package tech.kayys.syirkah.asset.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Maintenance record identifier.
 */
public record MaintenanceId(UUID value) implements DomainId<UUID>, Serializable {

    public MaintenanceId {
        Objects.requireNonNull(value, "MaintenanceId value cannot be null");
    }

    public static MaintenanceId of(UUID value) {
        return new MaintenanceId(value);
    }

    public static MaintenanceId generate() {
        return new MaintenanceId(UUID.randomUUID());
    }

    public static MaintenanceId fromString(String value) {
        return new MaintenanceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "MaintenanceId{" + value + "}";
    }
}