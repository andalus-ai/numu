package tech.kayys.syirkah.project.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.task.ProjectTaskId;

import java.util.Objects;

/**
 * Cancels a task that has not been completed yet.
 */
public record CancelTaskCommand(
        ProjectTaskId taskId
) implements Command {

    public CancelTaskCommand {
        Objects.requireNonNull(taskId, "taskId cannot be null");
    }
}