package tech.kayys.numou.kiosk.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.kiosk.domain.identifier.KioskId;
import tech.kayys.numou.kiosk.domain.valueobject.KioskStatus;

public record UpdateKioskStatusCommand(
        KioskId kioskId,
        KioskStatus status,
        String notes
) implements Command {
}
