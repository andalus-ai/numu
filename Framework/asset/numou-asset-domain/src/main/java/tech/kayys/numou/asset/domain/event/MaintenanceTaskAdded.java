package tech.kayys.numou.asset.domain.event;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MaintenanceTaskAdded(UUID eventId, Instant occurredAt, UUID workOrderId, UUID taskId, String taskNumber) implements DomainEvent {
    public MaintenanceTaskAdded { Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(workOrderId); Objects.requireNonNull(taskId); Objects.requireNonNull(taskNumber); }
    @Override public String eventType() { return "maintenance.task-added"; }
}
