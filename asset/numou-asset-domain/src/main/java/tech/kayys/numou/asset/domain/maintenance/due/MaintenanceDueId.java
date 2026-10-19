package tech.kayys.numou.asset.domain.maintenance.due;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Identifier for {@link MaintenanceDue} (ASSET-22 §13). */
public record MaintenanceDueId(UUID value) implements DomainId<UUID>, Serializable {
        public MaintenanceDueId {
        Objects.requireNonNull(value, "MaintenanceDueId value cannot be null");
    }
    public static MaintenanceDueId of(UUID value) { return new MaintenanceDueId(value); }
    public static MaintenanceDueId generate() { return new MaintenanceDueId(UUID.randomUUID()); }
    public static MaintenanceDueId fromString(String value) { return new MaintenanceDueId(UUID.fromString(value)); }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
