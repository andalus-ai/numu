package tech.kayys.syirkah.construction.domain.workforce.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record LaborRequirementCreated(
        UUID eventId,
        Instant occurredAt,
        UUID requirementId,
        UUID projectId,
        String trade
) implements DomainEvent {
    @Override public String eventType() { return "construction.labor-requirement-created"; }
}
