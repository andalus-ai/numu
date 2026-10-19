package tech.kayys.numou.project.application.commercial.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.commercial.ProjectClaimId;

import java.util.Objects;

public record RejectClaimCommand(
        ProjectClaimId claimId
) implements Command {

    public RejectClaimCommand {
        Objects.requireNonNull(claimId, "claimId cannot be null");
    }
}
