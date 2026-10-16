package tech.kayys.numou.project.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.Objects;

/** Completes an ACTIVE project. */
public record CompleteProjectCommand(
        ProjectId projectId
) implements Command {

    public CompleteProjectCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
