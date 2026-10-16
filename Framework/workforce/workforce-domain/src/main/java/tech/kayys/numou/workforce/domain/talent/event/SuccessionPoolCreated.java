package tech.kayys.numou.workforce.domain.talent.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.talent.SuccessionPoolId;

import java.time.Instant;
import java.util.UUID;

public record SuccessionPoolCreated(
        UUID eventId,
        Instant occurredAt,
        SuccessionPoolId id,
        TenantId tenantId,
        String code
) implements DomainEvent {
    public SuccessionPoolCreated(SuccessionPoolId id, TenantId tenantId, String code) {
        this(UUID.randomUUID(), Instant.now(), id, tenantId, code);
    }
    @Override public String eventType() { return "workforce.talent.succession_pool.created"; }
}
