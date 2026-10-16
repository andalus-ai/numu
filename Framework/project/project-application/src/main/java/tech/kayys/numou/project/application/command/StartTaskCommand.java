package tech.kayys.numou.project.application.command;

import tech.kayys.numou.project.domain.task.ProjectTaskId;

import java.util.Objects;

/**
 * Moves a TODO task into IN_PROGRESS.
 */
public record StartTaskCommand(
        ProjectTaskId taskId
) implements tech.kayys.numou.foundation.application.command.Command {

    public StartTaskCommand {
        Objects.requireNonNull(taskId, "taskId cannot be null");
    }
}
