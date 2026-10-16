package tech.kayys.syirkah.project.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.phase.ProjectPhaseId;

import java.util.Objects;

/** Starts a PLANNED phase. */
public record StartPhaseCommand(
        ProjectPhaseId phaseId
) implements Command {

    public StartPhaseCommand {
        Objects.requireNonNull(phaseId, "phaseId cannot be null");
    }
}
