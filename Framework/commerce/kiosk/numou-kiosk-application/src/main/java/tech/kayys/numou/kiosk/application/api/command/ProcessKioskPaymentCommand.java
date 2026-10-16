package tech.kayys.syirkah.kiosk.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskSessionId;

public record ProcessKioskPaymentCommand(
        KioskSessionId sessionId,
        String paymentMethod,
        String amount,
        String currencyCode,
        String cardToken,
        String transactionId
) implements Command {
}
