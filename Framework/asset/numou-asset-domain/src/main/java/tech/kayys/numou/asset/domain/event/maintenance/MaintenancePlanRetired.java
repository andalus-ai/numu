package tech.kayys.numou.asset.domain.event.maintenance;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MaintenancePlanRetired(UUID eventId, Instant occurredAt, UUID planId) implements DomainEvent {
    public MaintenancePlanRetired { Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(planId); }
    @Override public String eventType() { return "maintenance.plan-retired"; }
}
