package tech.kayys.syirkah.project.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.Objects;

/** Puts an ACTIVE project on hold. */
public record PutProjectOnHoldCommand(
        ProjectId projectId
) implements Command {

    public PutProjectOnHoldCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
