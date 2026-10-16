package tech.kayys.numou.project.application.commercial.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.valueobject.DateRange;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.project.domain.commercial.ContractType;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.Objects;

public record CreateProjectContractCommand(
        ProjectId projectId,
        ContractType contractType,
        DateRange contractPeriod,
        Money contractValue
) implements Command {

    public CreateProjectContractCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(contractType, "contractType cannot be null");
        Objects.requireNonNull(contractPeriod, "contractPeriod cannot be null");
        Objects.requireNonNull(contractValue, "contractValue cannot be null");
    }
}
