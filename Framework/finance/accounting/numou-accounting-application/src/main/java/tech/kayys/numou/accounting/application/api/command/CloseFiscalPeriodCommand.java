package tech.kayys.numou.accounting.application.api.command;

import tech.kayys.numou.accounting.application.cqrs.Command;
import tech.kayys.numou.accounting.domain.identifier.FiscalPeriodId;

import java.util.Objects;

public record CloseFiscalPeriodCommand(
        FiscalPeriodId periodId,
        String closedBy
) implements Command {
    public CloseFiscalPeriodCommand {
        Objects.requireNonNull(periodId, "periodId cannot be null");
    }
}
