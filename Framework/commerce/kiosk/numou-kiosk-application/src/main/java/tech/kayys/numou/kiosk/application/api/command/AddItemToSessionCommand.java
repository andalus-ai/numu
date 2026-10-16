package tech.kayys.syirkah.kiosk.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskSessionId;

import java.util.UUID;

public record AddItemToSessionCommand(
        KioskSessionId sessionId,
        UUID productId,
        int quantity,
        String variationId
) implements Command {
}
