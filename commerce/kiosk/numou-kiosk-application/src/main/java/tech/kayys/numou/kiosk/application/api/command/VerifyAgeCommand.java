package tech.kayys.numou.kiosk.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.kiosk.domain.identifier.KioskSessionId;

public record VerifyAgeCommand(
        KioskSessionId sessionId,
        boolean verified,
        String verifiedBy
) implements Command {
}
