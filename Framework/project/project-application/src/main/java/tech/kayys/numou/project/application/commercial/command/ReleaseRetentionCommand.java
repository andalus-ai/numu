package tech.kayys.syirkah.project.application.commercial.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.ProjectRetentionId;

import java.util.Objects;

public record ReleaseRetentionCommand(
        ProjectRetentionId retentionId,
        Money amount
) implements Command {

    public ReleaseRetentionCommand {
        Objects.requireNonNull(retentionId, "retentionId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
    }
}
