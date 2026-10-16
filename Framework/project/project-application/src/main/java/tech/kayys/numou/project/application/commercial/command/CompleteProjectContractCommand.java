package tech.kayys.syirkah.project.application.commercial.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.commercial.ProjectContractId;

import java.util.Objects;

public record CompleteProjectContractCommand(
        ProjectContractId contractId
) implements Command {

    public CompleteProjectContractCommand {
        Objects.requireNonNull(contractId, "contractId cannot be null");
    }
}
