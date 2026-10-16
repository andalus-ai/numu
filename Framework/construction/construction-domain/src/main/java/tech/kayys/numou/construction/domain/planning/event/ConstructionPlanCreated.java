package tech.kayys.syirkah.construction.domain.planning.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record ConstructionPlanCreated(
        UUID eventId,
        Instant occurredAt,
        UUID planId,
        UUID projectId,
        String planName
) implements DomainEvent {
    @Override public String eventType() { return "construction.plan-created"; }
}
