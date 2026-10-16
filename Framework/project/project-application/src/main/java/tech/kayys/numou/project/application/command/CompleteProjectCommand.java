package tech.kayys.syirkah.project.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.Objects;

/** Completes an ACTIVE project. */
public record CompleteProjectCommand(
        ProjectId projectId
) implements Command {

    public CompleteProjectCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
