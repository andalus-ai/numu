package tech.kayys.numou.workforce.domain.talent.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.talent.TalentReviewCycleId;

import java.time.Instant;
import java.util.UUID;

public record TalentReviewCycleOpened(
        UUID eventId,
        Instant occurredAt,
        TalentReviewCycleId id,
        TenantId tenantId,
        String code
) implements DomainEvent {
    public TalentReviewCycleOpened(TalentReviewCycleId id, TenantId tenantId, String code) {
        this(UUID.randomUUID(), Instant.now(), id, tenantId, code);
    }
    @Override public String eventType() { return "workforce.talent.review_cycle.opened"; }
}
