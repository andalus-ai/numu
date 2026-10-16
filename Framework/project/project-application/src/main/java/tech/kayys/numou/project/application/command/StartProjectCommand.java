package tech.kayys.syirkah.project.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.Objects;

/** Starts a PLANNED project and activates it. */
public record StartProjectCommand(
        ProjectId projectId
) implements Command {

    public StartProjectCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
