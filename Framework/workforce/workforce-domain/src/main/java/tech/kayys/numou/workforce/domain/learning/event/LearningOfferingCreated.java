package tech.kayys.numou.workforce.domain.learning.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.learning.LearningOfferingId;
import tech.kayys.numou.workforce.domain.learning.LearningProgramId;

import java.time.Instant;
import java.util.UUID;

public record LearningOfferingCreated(
        UUID eventId,
        Instant occurredAt,
        LearningOfferingId id,
        TenantId tenantId,
        LearningProgramId programId,
        String code
) implements DomainEvent {
    public LearningOfferingCreated(LearningOfferingId id, TenantId tenantId, LearningProgramId programId, String code) {
        this(UUID.randomUUID(), Instant.now(), id, tenantId, programId, code);
    }
    @Override public String eventType() { return "workforce.learning.offering.created"; }
}
