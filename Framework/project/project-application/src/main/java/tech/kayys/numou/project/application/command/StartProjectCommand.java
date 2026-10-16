package tech.kayys.numou.project.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.Objects;

/** Starts a PLANNED project and activates it. */
public record StartProjectCommand(
        ProjectId projectId
) implements Command {

    public StartProjectCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
