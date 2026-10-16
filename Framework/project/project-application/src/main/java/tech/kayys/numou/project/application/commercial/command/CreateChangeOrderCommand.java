package tech.kayys.syirkah.project.application.commercial.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.commercial.ChangeImpact;
import tech.kayys.syirkah.project.domain.commercial.ChangeRequestId;
import tech.kayys.syirkah.project.domain.commercial.ProjectContractId;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.Objects;

public record CreateChangeOrderCommand(
        ProjectId projectId,
        ProjectContractId contractId,
        ChangeRequestId changeRequestId,
        String number,
        String title,
        String description,
        ChangeImpact impact
) implements Command {

    public CreateChangeOrderCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(contractId, "contractId cannot be null");
        Objects.requireNonNull(number, "number cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(impact, "impact cannot be null");
    }
}
