package tech.kayys.numou.groceries.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.groceries.domain.identifier.ScaleId;

public record TareScaleCommand(ScaleId scaleId) implements Command {
    public TareScaleCommand {
        if (scaleId == null) throw new IllegalArgumentException("Scale ID cannot be null");
    }
}
