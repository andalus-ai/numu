package tech.kayys.syirkah.project.application.commercial.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.commercial.ChangeOrderId;

import java.util.Objects;

public record ApplyChangeOrderCommand(
        ChangeOrderId changeOrderId
) implements Command {

    public ApplyChangeOrderCommand {
        Objects.requireNonNull(changeOrderId, "changeOrderId cannot be null");
    }
}
