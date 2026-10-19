package tech.kayys.numou.asset.domain.event;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MaintenanceWorkOrderHeld(UUID eventId, Instant occurredAt, UUID workOrderId, UUID assetId) implements DomainEvent {
    public MaintenanceWorkOrderHeld { Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(workOrderId); Objects.requireNonNull(assetId); }
    @Override public String eventType() { return "maintenance.work-order-held"; }
}
