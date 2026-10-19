package tech.kayys.numou.workforce.domain.performance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.performance.PerformanceCycleId;

import java.time.Instant;
import java.util.UUID;

public record PerformanceCycleCreated(
        UUID eventId,
        Instant occurredAt,
        PerformanceCycleId id,
        TenantId tenantId,
        String code
) implements DomainEvent {
    public PerformanceCycleCreated(PerformanceCycleId id, TenantId tenantId, String code) {
        this(UUID.randomUUID(), Instant.now(), id, tenantId, code);
    }
    @Override public String eventType() { return "workforce.performance.cycle.created"; }
}
