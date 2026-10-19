package tech.kayys.numou.workforce.domain.analytics.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.analytics.WorkforcePlanId;

import java.time.Instant;
import java.util.UUID;

public record WorkforcePlanApproved(
        UUID eventId,
        Instant occurredAt,
        WorkforcePlanId id,
        String approvedBy
) implements DomainEvent {
    public WorkforcePlanApproved(WorkforcePlanId id, String approvedBy) {
        this(UUID.randomUUID(), Instant.now(), id, approvedBy);
    }
    @Override public String eventType() { return "workforce.analytics.plan.approved"; }
}
