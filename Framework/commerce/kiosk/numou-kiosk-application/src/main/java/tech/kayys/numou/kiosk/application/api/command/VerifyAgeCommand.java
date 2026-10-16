package tech.kayys.syirkah.kiosk.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskSessionId;

public record VerifyAgeCommand(
        KioskSessionId sessionId,
        boolean verified,
        String verifiedBy
) implements Command {
}
