package tech.kayys.numou.workforce.domain.talent.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.talent.TalentSegmentId;

import java.time.Instant;
import java.util.UUID;

public record TalentSegmentDefined(
        UUID eventId,
        Instant occurredAt,
        TalentSegmentId id,
        TenantId tenantId,
        String code
) implements DomainEvent {
    public TalentSegmentDefined(TalentSegmentId id, TenantId tenantId, String code) {
        this(UUID.randomUUID(), Instant.now(), id, tenantId, code);
    }
    @Override public String eventType() { return "workforce.talent.segment.defined"; }
}
