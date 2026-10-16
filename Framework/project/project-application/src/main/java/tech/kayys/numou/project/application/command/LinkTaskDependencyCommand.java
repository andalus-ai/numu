package tech.kayys.syirkah.project.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.task.ProjectTaskId;
import tech.kayys.syirkah.project.domain.task.TaskDependencyType;

import java.util.Objects;

/**
 * Links a successor task behind a predecessor task.
 *
 * Both tasks must exist and belong to the same project; a task can
 * never depend on itself (enforced by the aggregate as well).
 */
public record LinkTaskDependencyCommand(
        ProjectId projectId,
        ProjectTaskId predecessorTaskId,
        ProjectTaskId successorTaskId,
        TaskDependencyType dependencyType,
        int lagDays
) implements Command {

    public LinkTaskDependencyCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(predecessorTaskId, "predecessorTaskId cannot be null");
        Objects.requireNonNull(successorTaskId, "successorTaskId cannot be null");
        Objects.requireNonNull(dependencyType, "dependencyType cannot be null");
    }
}
