package tech.kayys.numou.asset.domain.maintenance.workorder;
import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record MaintenanceWorkOrderId(UUID value) implements DomainId<UUID>, Serializable {
        public MaintenanceWorkOrderId {
        Objects.requireNonNull(value, "MaintenanceWorkOrderId value cannot be null");
    }
    public static MaintenanceWorkOrderId of(UUID value) { return new MaintenanceWorkOrderId(value); }
    public static MaintenanceWorkOrderId generate() { return new MaintenanceWorkOrderId(UUID.randomUUID()); }
    public static MaintenanceWorkOrderId fromString(String value) { return new MaintenanceWorkOrderId(UUID.fromString(value)); }
    @Override public String toString() { return "MaintenanceWorkOrderId{" + value + "}"; }
}
