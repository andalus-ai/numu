package tech.kayys.numou.workforce.domain.talent.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.talent.CriticalPositionId;
import tech.kayys.numou.workforce.domain.talent.SuccessionPlanId;

import java.time.Instant;
import java.util.UUID;

public record SuccessionPlanCreated(
        UUID eventId,
        Instant occurredAt,
        SuccessionPlanId id,
        TenantId tenantId,
        CriticalPositionId criticalPositionId
) implements DomainEvent {
    public SuccessionPlanCreated(SuccessionPlanId id, TenantId tenantId, CriticalPositionId criticalPositionId) {
        this(UUID.randomUUID(), Instant.now(), id, tenantId, criticalPositionId);
    }
    @Override public String eventType() { return "workforce.talent.succession_plan.created"; }
}
