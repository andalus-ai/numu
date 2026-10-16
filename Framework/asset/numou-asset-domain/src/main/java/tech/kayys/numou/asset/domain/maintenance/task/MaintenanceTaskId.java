package tech.kayys.numou.asset.domain.maintenance.task;
import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record MaintenanceTaskId(UUID value) implements DomainId<UUID>, Serializable {
        public MaintenanceTaskId {
        Objects.requireNonNull(value, "MaintenanceTaskId value cannot be null");
    }
    public static MaintenanceTaskId of(UUID value) { return new MaintenanceTaskId(value); }
    public static MaintenanceTaskId generate() { return new MaintenanceTaskId(UUID.randomUUID()); }
    public static MaintenanceTaskId fromString(String v) { return new MaintenanceTaskId(UUID.fromString(v)); }
    @Override public String toString() { return "MaintenanceTaskId{" + value + "}"; }
}
