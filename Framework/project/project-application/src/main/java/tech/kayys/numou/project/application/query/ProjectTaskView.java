package tech.kayys.syirkah.project.application.query;

import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.domain.phase.ProjectPhaseId;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.task.ProjectTask;
import tech.kayys.syirkah.project.domain.task.ProjectTaskId;
import tech.kayys.syirkah.project.domain.task.TaskNumber;
import tech.kayys.syirkah.project.domain.task.TaskPriority;
import tech.kayys.syirkah.project.domain.task.TaskStatus;
import tech.kayys.syirkah.project.domain.task.TaskType;

import java.util.Objects;

/** Read model of a project task. */
public record ProjectTaskView(
        ProjectTaskId taskId,
        ProjectId projectId,
        ProjectPhaseId phaseId,
        TaskNumber taskNumber,
        String title,
        TaskType type,
        TaskStatus status,
        TaskPriority priority,
        DateRange plannedPeriod,
        int progressPercentage
) {

    public ProjectTaskView {
        Objects.requireNonNull(taskId, "taskId cannot be null");
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(taskNumber, "taskNumber cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(priority, "priority cannot be null");
    }

    public static ProjectTaskView from(ProjectTask task) {
        return new ProjectTaskView(
                task.id(),
                task.projectId(),
                task.phaseId(),
                task.taskNumber(),
                task.title(),
                task.type(),
                task.status(),
                task.priority(),
                task.plannedPeriod(),
                task.progressPercentage()
        );
    }
}
