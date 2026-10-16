package tech.kayys.numou.project.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.Objects;

/** Puts an ACTIVE project on hold. */
public record PutProjectOnHoldCommand(
        ProjectId projectId
) implements Command {

    public PutProjectOnHoldCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
