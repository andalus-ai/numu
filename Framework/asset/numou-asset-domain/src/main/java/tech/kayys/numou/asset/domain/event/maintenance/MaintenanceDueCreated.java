package tech.kayys.numou.asset.domain.event.maintenance;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MaintenanceDueCreated(UUID eventId, Instant occurredAt, UUID dueId, UUID assetId, String status) implements DomainEvent {
    public MaintenanceDueCreated { Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(dueId); Objects.requireNonNull(assetId); Objects.requireNonNull(status); }
    @Override public String eventType() { return "maintenance.due-created"; }
}
