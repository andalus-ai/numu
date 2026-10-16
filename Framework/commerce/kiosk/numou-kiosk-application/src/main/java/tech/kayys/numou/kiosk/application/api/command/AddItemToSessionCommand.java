package tech.kayys.numou.kiosk.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.kiosk.domain.identifier.KioskSessionId;

import java.util.UUID;

public record AddItemToSessionCommand(
        KioskSessionId sessionId,
        UUID productId,
        int quantity,
        String variationId
) implements Command {
}
