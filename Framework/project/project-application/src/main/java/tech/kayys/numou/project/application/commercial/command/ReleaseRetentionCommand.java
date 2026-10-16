package tech.kayys.numou.project.application.commercial.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.project.domain.commercial.ProjectRetentionId;

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
