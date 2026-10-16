package tech.kayys.numou.project.application.commercial.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.project.domain.commercial.ProjectAdvanceId;

import java.util.Objects;

public record ApplyAdvanceCommand(
        ProjectAdvanceId advanceId,
        Money amount
) implements Command {

    public ApplyAdvanceCommand {
        Objects.requireNonNull(advanceId, "advanceId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
    }
}
