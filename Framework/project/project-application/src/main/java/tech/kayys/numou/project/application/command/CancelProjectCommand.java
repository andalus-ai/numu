package tech.kayys.numou.project.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.Objects;

/** Cancels a project that is not completed or already cancelled. */
public record CancelProjectCommand(
        ProjectId projectId
) implements Command {

    public CancelProjectCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
