package tech.kayys.syirkah.project.application.commercial.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.ProjectClaimId;

import java.util.Objects;

public record AcceptClaimCommand(
        ProjectClaimId claimId,
        Money acceptedAmount
) implements Command {

    public AcceptClaimCommand {
        Objects.requireNonNull(claimId, "claimId cannot be null");
        Objects.requireNonNull(acceptedAmount, "acceptedAmount cannot be null");
    }
}
