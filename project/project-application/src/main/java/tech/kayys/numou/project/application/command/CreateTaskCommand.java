package tech.kayys.numou.project.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.valueobject.DateRange;
import tech.kayys.numou.project.domain.milestone.ProjectMilestoneId;
import tech.kayys.numou.project.domain.phase.ProjectPhaseId;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.task.ProjectTaskId;
import tech.kayys.numou.project.domain.task.TaskNumber;
import tech.kayys.numou.project.domain.task.TaskPriority;
import tech.kayys.numou.project.domain.task.TaskType;

import java.util.Objects;

/**
 * Creates a task inside an existing project.
 *
 * The identifiers in the payload are not trusted blindly: the
 * handler verifies that the project, phase, and milestone actually
 * exist before creating the task (see project01.md section 14).
 */
public record CreateTaskCommand(
        ProjectId projectId,
        ProjectPhaseId phaseId,
        ProjectMilestoneId milestoneId,
        ProjectTaskId parentTaskId,
        TaskNumber taskNumber,
        String title,
        String description,
        TaskType type,
        TaskPriority priority,
        DateRange plannedPeriod
) implements Command {

    public CreateTaskCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(taskNumber, "taskNumber cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(priority, "priority cannot be null");
    }
}
