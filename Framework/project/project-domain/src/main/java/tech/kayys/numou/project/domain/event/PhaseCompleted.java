package tech.kayys.numou.project.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.project.domain.phase.ProjectPhaseId;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.UUID;

public record PhaseCompleted(
        UUID eventId,
        Instant occurredAt,
        ProjectPhaseId phaseId,
        ProjectId projectId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.phase-completed";
    }
}
