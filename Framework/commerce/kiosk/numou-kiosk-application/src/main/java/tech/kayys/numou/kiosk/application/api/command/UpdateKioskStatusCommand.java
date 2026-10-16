package tech.kayys.syirkah.kiosk.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskId;
import tech.kayys.syirkah.kiosk.domain.valueobject.KioskStatus;

public record UpdateKioskStatusCommand(
        KioskId kioskId,
        KioskStatus status,
        String notes
) implements Command {
}
