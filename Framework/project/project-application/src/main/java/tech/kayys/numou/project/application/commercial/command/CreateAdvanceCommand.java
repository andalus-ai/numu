package tech.kayys.numou.project.application.commercial.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.project.domain.commercial.ProjectContractId;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.Objects;

public record CreateAdvanceCommand(
        ProjectId projectId,
        ProjectContractId contractId,
        Money amount
) implements Command {

    public CreateAdvanceCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(contractId, "contractId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
    }
}
