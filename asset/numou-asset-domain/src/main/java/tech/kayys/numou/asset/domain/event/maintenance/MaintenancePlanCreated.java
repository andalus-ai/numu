package tech.kayys.numou.asset.domain.event.maintenance;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MaintenancePlanCreated(UUID eventId, Instant occurredAt, UUID planId, String planNumber) implements DomainEvent {
    public MaintenancePlanCreated { Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(planId); Objects.requireNonNull(planNumber); }
    @Override public String eventType() { return "maintenance.plan-created"; }
}
