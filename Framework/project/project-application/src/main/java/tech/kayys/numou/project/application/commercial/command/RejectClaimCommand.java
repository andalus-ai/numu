package tech.kayys.syirkah.project.application.commercial.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.commercial.ProjectClaimId;

import java.util.Objects;

public record RejectClaimCommand(
        ProjectClaimId claimId
) implements Command {

    public RejectClaimCommand {
        Objects.requireNonNull(claimId, "claimId cannot be null");
    }
}
