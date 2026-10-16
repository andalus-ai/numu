package tech.kayys.numou.accounting.application.api.command;

import tech.kayys.numou.accounting.application.cqrs.Command;
import tech.kayys.numou.accounting.domain.identifier.AccountId;

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
