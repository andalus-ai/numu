package tech.kayys.syirkah.project.application.commercial.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.ProjectAdvanceId;

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
