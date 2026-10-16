package tech.kayys.syirkah.project.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.phase.ProjectPhaseId;

import java.util.Objects;

/** Completes an ACTIVE phase. */
public record CompletePhaseCommand(
        ProjectPhaseId phaseId
) implements Command {

    public CompletePhaseCommand {
        Objects.requireNonNull(phaseId, "phaseId cannot be null");
    }
}
