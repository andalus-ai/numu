package tech.kayys.syirkah.project.application.commercial.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.commercial.ChangeOrderId;

import java.util.Objects;

public record ApproveChangeOrderCommand(
        ChangeOrderId changeOrderId
) implements Command {

    public ApproveChangeOrderCommand {
        Objects.requireNonNull(changeOrderId, "changeOrderId cannot be null");
    }
}
