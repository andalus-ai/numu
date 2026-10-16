package tech.kayys.numou.project.application.commercial.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.commercial.ChangeOrderId;

import java.util.Objects;

public record ApproveChangeOrderCommand(
        ChangeOrderId changeOrderId
) implements Command {

    public ApproveChangeOrderCommand {
        Objects.requireNonNull(changeOrderId, "changeOrderId cannot be null");
    }
}
