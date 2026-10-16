package tech.kayys.numou.workforce.domain.analytics.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.analytics.WorkforceDemandId;
import tech.kayys.numou.workforce.domain.position.PositionId;

import java.time.Instant;
import java.util.UUID;

public record WorkforceDemandCreated(
        UUID eventId,
        Instant occurredAt,
        WorkforceDemandId id,
        TenantId tenantId,
        PositionId positionId,
        int quantity
) implements DomainEvent {
    public WorkforceDemandCreated(WorkforceDemandId id, TenantId tenantId, PositionId positionId, int quantity) {
        this(UUID.randomUUID(), Instant.now(), id, tenantId, positionId, quantity);
    }
    @Override public String eventType() { return "workforce.analytics.demand.created"; }
}
