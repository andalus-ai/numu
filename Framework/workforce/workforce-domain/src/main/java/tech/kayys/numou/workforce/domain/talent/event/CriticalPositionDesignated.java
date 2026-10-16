package tech.kayys.numou.workforce.domain.talent.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.position.PositionId;
import tech.kayys.numou.workforce.domain.talent.CriticalPositionId;
import tech.kayys.numou.workforce.domain.talent.Criticality;

import java.time.Instant;
import java.util.UUID;

public record CriticalPositionDesignated(
        UUID eventId,
        Instant occurredAt,
        CriticalPositionId id,
        TenantId tenantId,
        PositionId positionId,
        Criticality criticality
) implements DomainEvent {
    public CriticalPositionDesignated(CriticalPositionId id, TenantId tenantId, PositionId positionId, Criticality criticality) {
        this(UUID.randomUUID(), Instant.now(), id, tenantId, positionId, criticality);
    }
    @Override public String eventType() { return "workforce.talent.critical_position.designated"; }
}
