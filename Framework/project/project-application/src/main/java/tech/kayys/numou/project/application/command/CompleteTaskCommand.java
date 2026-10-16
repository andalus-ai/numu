package tech.kayys.numou.project.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.task.ProjectTaskId;

import java.util.Objects;

/**
 * Marks an in-progress task DONE with 100% progress.
 */
public record CompleteTaskCommand(
        ProjectTaskId taskId
) implements Command {

    public CompleteTaskCommand {
        Objects.requireNonNull(taskId, "taskId cannot be null");
    }
}