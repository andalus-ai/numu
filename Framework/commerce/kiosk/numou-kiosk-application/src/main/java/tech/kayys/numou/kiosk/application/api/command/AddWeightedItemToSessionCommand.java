package tech.kayys.syirkah.kiosk.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskSessionId;

import java.util.UUID;

public record AddWeightedItemToSessionCommand(
        KioskSessionId sessionId,
        UUID groceryProductId,
        UUID scaleId,
        double weight,
        String weightUnit
) implements Command {
}
