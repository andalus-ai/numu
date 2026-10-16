package tech.kayys.syirkah.project.application.commercial.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.ClaimType;
import tech.kayys.syirkah.project.domain.commercial.ProjectContractId;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.Objects;

public record CreateClaimCommand(
        ProjectId projectId,
        ProjectContractId contractId,
        ClaimType type,
        String number,
        String title,
        String description,
        Money claimedAmount
) implements Command {

    public CreateClaimCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(contractId, "contractId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(number, "number cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(claimedAmount, "claimedAmount cannot be null");
    }
}
