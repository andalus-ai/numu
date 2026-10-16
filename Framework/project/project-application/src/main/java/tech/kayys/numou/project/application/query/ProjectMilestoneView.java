package tech.kayys.syirkah.project.application.query;

import tech.kayys.syirkah.project.domain.milestone.MilestoneStatus;
import tech.kayys.syirkah.project.domain.milestone.MilestoneType;
import tech.kayys.syirkah.project.domain.milestone.ProjectMilestone;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Read model of a project milestone. */
public record ProjectMilestoneView(
        UUID milestoneId,
        UUID projectId,
        int sequence,
        String name,
        MilestoneType type,
        MilestoneStatus status,
        LocalDate plannedDate,
        LocalDate actualDate
) {

    public ProjectMilestoneView {
        Objects.requireNonNull(milestoneId, "milestoneId cannot be null");
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(plannedDate, "plannedDate cannot be null");
    }

    public static ProjectMilestoneView from(ProjectMilestone milestone) {
        return new ProjectMilestoneView(
                milestone.id().value(),
                milestone.projectId().value(),
                milestone.sequence(),
                milestone.name(),
                milestone.type(),
                milestone.status(),
                milestone.plannedDate(),
                milestone.actualDate()
        );
    }
}
