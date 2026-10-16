package tech.kayys.syirkah.project.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.task.ProjectTaskId;

import java.util.Objects;

/**
 * Moves a BLOCKED task back into IN_PROGRESS.
 */
public record UnblockTaskCommand(
        ProjectTaskId taskId
) implements Command {

    public UnblockTaskCommand {
        Objects.requireNonNull(taskId, "taskId cannot be null");
    }
}