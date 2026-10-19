package tech.kayys.numou.project.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.task.ProjectTaskId;
import tech.kayys.numou.project.domain.task.TaskAssignmentId;

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
