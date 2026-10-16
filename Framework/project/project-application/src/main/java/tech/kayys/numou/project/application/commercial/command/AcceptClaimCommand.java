package tech.kayys.numou.project.application.commercial.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.project.domain.commercial.ProjectClaimId;

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
