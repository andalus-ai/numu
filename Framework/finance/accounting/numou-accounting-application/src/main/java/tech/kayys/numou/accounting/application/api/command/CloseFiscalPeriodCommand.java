package tech.kayys.syirkah.accounting.application.api.command;

import tech.kayys.syirkah.accounting.application.cqrs.Command;
import tech.kayys.syirkah.accounting.domain.identifier.FiscalPeriodId;

import java.util.Objects;

public record CloseFiscalPeriodCommand(
        FiscalPeriodId periodId,
        String closedBy
) implements Command {
    public CloseFiscalPeriodCommand {
        Objects.requireNonNull(periodId, "periodId cannot be null");
    }
}
