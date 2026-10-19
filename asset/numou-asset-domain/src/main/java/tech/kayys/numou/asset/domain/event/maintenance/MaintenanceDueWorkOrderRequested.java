package tech.kayys.numou.asset.domain.event.maintenance;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Emitted when a due generates a work order via the event path (ASSET-19 worker owns the WO aggregate).
 */
public record MaintenanceDueWorkOrderRequested(UUID eventId, Instant occurredAt, UUID dueId, UUID assetId, UUID workOrderId) implements DomainEvent {
    public MaintenanceDueWorkOrderRequested { Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(dueId); Objects.requireNonNull(assetId); Objects.requireNonNull(workOrderId); }
    @Override public String eventType() { return "maintenance.preventive-work-order-generated"; }
}
