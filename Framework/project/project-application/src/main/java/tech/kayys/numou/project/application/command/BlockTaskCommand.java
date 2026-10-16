package tech.kayys.numou.project.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.task.ProjectTaskId;

import java.util.Objects;

/**
 * Moves an in-progress task into BLOCKED.
 */
public record BlockTaskCommand(
        ProjectTaskId taskId
) implements Command {

    public BlockTaskCommand {
        Objects.requireNonNull(taskId, "taskId cannot be null");
    }
}