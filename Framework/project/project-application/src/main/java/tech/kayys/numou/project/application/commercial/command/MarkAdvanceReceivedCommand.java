package tech.kayys.numou.project.application.commercial.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.commercial.ProjectAdvanceId;

import java.util.Objects;

public record MarkAdvanceReceivedCommand(
        ProjectAdvanceId advanceId
) implements Command {

    public MarkAdvanceReceivedCommand {
        Objects.requireNonNull(advanceId, "advanceId cannot be null");
    }
}
