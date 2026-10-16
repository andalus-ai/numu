package tech.kayys.syirkah.groceries.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.groceries.domain.identifier.ScaleId;

public record TareScaleCommand(ScaleId scaleId) implements Command {
    public TareScaleCommand {
        if (scaleId == null) throw new IllegalArgumentException("Scale ID cannot be null");
    }
}
