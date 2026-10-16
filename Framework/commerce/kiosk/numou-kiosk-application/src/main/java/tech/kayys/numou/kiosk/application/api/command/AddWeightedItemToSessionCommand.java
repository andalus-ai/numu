package tech.kayys.numou.kiosk.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.kiosk.domain.identifier.KioskSessionId;

import java.util.UUID;

public record AddWeightedItemToSessionCommand(
        KioskSessionId sessionId,
        UUID groceryProductId,
        UUID scaleId,
        double weight,
        String weightUnit
) implements Command {
}
