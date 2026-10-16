package tech.kayys.syirkah.project.application.commercial.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.ProjectContractId;
import tech.kayys.syirkah.project.domain.project.ProjectId;

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
