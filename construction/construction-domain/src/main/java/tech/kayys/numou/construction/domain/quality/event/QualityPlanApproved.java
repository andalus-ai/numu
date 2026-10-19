package tech.kayys.numou.construction.domain.quality.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record QualityPlanApproved(
        UUID eventId,
        Instant occurredAt,
        UUID planId,
        UUID projectId
) implements DomainEvent {
    @Override public String eventType() { return "construction.quality-plan-approved"; }
}
