package tech.kayys.syirkah.project.application.query;

import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.domain.project.Project;
import tech.kayys.syirkah.project.domain.project.ProjectStatus;
import tech.kayys.syirkah.project.domain.project.ProjectType;

import java.util.Objects;
import java.util.UUID;

/** Read model of a project for the inbound (REST/messaging) adapters. */
public record ProjectView(
        UUID projectId,
        String projectNumber,
        String name,
        ProjectType type,
        ProjectStatus status,
        UUID customerId,
        DateRange plannedPeriod
) {

    public ProjectView {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(projectNumber, "projectNumber cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
    }

    public static ProjectView from(Project project) {
        return new ProjectView(
                project.id().value(),
                project.projectNumber().value(),
                project.name(),
                project.type(),
                project.status(),
                project.customerId(),
                project.plannedPeriod()
        );
    }
}
