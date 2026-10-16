package tech.kayys.syirkah.project.application.commercial.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.ContractType;
import tech.kayys.syirkah.project.domain.project.ProjectId;

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
