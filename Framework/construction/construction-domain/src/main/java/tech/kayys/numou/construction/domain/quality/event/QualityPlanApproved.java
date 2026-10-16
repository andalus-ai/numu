package tech.kayys.syirkah.construction.domain.quality.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
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
