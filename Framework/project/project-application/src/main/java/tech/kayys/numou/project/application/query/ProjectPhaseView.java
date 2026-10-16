package tech.kayys.numou.project.application.query;

import tech.kayys.numou.foundation.domain.valueobject.DateRange;
import tech.kayys.numou.project.domain.phase.PhaseStatus;
import tech.kayys.numou.project.domain.phase.PhaseType;
import tech.kayys.numou.project.domain.phase.ProjectPhase;

import java.util.Objects;
import java.util.UUID;

/** Read model of a project phase. */
public record ProjectPhaseView(
        UUID phaseId,
        UUID projectId,
        int sequence,
        String name,
        PhaseType type,
        PhaseStatus status,
        DateRange plannedPeriod
) {

    public ProjectPhaseView {
        Objects.requireNonNull(phaseId, "phaseId cannot be null");
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
    }

    public static ProjectPhaseView from(ProjectPhase phase) {
        return new ProjectPhaseView(
                phase.id().value(),
                phase.projectId().value(),
                phase.sequence(),
                phase.name(),
                phase.type(),
                phase.status(),
                phase.plannedPeriod()
        );
    }
}
