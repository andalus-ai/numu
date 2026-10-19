package tech.kayys.numou.project.application.commercial.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.commercial.ProjectContractId;

import java.util.Objects;

public record ApproveProjectContractCommand(
        ProjectContractId contractId
) implements Command {

    public ApproveProjectContractCommand {
        Objects.requireNonNull(contractId, "contractId cannot be null");
    }
}
