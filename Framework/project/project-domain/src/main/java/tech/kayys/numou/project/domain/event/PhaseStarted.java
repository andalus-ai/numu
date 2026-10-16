package tech.kayys.syirkah.project.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.project.domain.phase.ProjectPhaseId;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.UUID;

public record PhaseStarted(
        UUID eventId,
        Instant occurredAt,
        ProjectPhaseId phaseId,
        ProjectId projectId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.phase-started";
    }
}
