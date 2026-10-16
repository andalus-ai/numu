package tech.kayys.numou.workforce.domain.performance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.performance.CompetencyId;

import java.time.Instant;
import java.util.UUID;

public record CompetencyCreated(
        UUID eventId,
        Instant occurredAt,
        CompetencyId id,
        TenantId tenantId,
        String code
) implements DomainEvent {
    public CompetencyCreated(CompetencyId id, TenantId tenantId, String code) {
        this(UUID.randomUUID(), Instant.now(), id, tenantId, code);
    }
    @Override public String eventType() { return "workforce.performance.competency.created"; }
}
