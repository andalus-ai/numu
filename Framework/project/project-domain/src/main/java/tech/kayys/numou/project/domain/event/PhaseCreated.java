package tech.kayys.syirkah.project.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.project.domain.phase.ProjectPhaseId;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.UUID;

public record PhaseCreated(
        UUID eventId,
        Instant occurredAt,
        ProjectPhaseId phaseId,
        ProjectId projectId,
        String name
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.phase-created";
    }
}
