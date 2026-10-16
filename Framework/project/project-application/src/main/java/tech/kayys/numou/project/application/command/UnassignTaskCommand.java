package tech.kayys.syirkah.project.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.task.ProjectTaskId;
import tech.kayys.syirkah.project.domain.task.TaskAssignmentId;

import java.util.Objects;

/**
 * Releases an assignment: the task stays, the resource leaves.
 */
public record UnassignTaskCommand(
        ProjectId projectId,
        ProjectTaskId taskId,
        TaskAssignmentId assignmentId
) implements Command {

    public UnassignTaskCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(taskId, "taskId cannot be null");
        Objects.requireNonNull(assignmentId, "assignmentId cannot be null");
    }
}
