package tech.kayys.numou.project.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.project.domain.phase.ProjectPhaseId;
import tech.kayys.numou.project.domain.project.ProjectId;

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
