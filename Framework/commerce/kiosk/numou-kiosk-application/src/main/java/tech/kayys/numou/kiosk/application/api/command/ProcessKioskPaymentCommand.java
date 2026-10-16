package tech.kayys.numou.kiosk.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.kiosk.domain.identifier.KioskSessionId;

public record ProcessKioskPaymentCommand(
        KioskSessionId sessionId,
        String paymentMethod,
        String amount,
        String currencyCode,
        String cardToken,
        String transactionId
) implements Command {
}
