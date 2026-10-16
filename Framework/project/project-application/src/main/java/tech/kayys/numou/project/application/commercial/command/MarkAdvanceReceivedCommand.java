package tech.kayys.syirkah.project.application.commercial.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.commercial.ProjectAdvanceId;

import java.util.Objects;

public record MarkAdvanceReceivedCommand(
        ProjectAdvanceId advanceId
) implements Command {

    public MarkAdvanceReceivedCommand {
        Objects.requireNonNull(advanceId, "advanceId cannot be null");
    }
}
