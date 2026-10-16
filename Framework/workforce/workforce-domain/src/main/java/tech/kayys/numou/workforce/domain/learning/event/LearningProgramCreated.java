package tech.kayys.numou.workforce.domain.learning.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.learning.LearningProgramId;

import java.time.Instant;
import java.util.UUID;

public record LearningProgramCreated(
        UUID eventId,
        Instant occurredAt,
        LearningProgramId id,
        TenantId tenantId,
        String code,
        String name
) implements DomainEvent {
    public LearningProgramCreated(LearningProgramId id, TenantId tenantId, String code, String name) {
        this(UUID.randomUUID(), Instant.now(), id, tenantId, code, name);
    }
    @Override public String eventType() { return "workforce.learning.program.created"; }
}
