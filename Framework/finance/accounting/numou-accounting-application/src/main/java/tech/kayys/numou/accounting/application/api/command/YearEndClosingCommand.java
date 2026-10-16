package tech.kayys.syirkah.accounting.application.api.command;

import tech.kayys.syirkah.accounting.application.cqrs.Command;
import tech.kayys.syirkah.accounting.domain.identifier.AccountId;

import java.util.Objects;

public record YearEndClosingCommand(
        int year,
        AccountId retainedEarningsAccountId,
        String closedBy
) implements Command {
    public YearEndClosingCommand {
        Objects.requireNonNull(retainedEarningsAccountId, "retainedEarningsAccountId cannot be null");
    }
}
