package tech.kayys.numou.wallet.application.payment;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

/** The callback was not signed by the provider (HTTP 401). The reason is deliberately vague. */
public final class InvalidSignatureException extends BusinessRuleViolation {
    public InvalidSignatureException() {
        super("Invalid signature");
    }
}
