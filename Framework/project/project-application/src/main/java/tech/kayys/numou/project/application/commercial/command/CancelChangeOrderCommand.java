package tech.kayys.syirkah.project.application.commercial.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.commercial.ChangeOrderId;

import java.util.Objects;

public record CancelChangeOrderCommand(
        ChangeOrderId changeOrderId
) implements Command {

    public CancelChangeOrderCommand {
        Objects.requireNonNull(changeOrderId, "changeOrderId cannot be null");
    }
}
