package tech.kayys.numou.asset.domain.event;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MaintenanceTaskCompleted(UUID eventId, Instant occurredAt, UUID workOrderId, UUID taskId) implements DomainEvent {
    public MaintenanceTaskCompleted { Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(workOrderId); Objects.requireNonNull(taskId); }
    @Override public String eventType() { return "maintenance.task-completed"; }
}
