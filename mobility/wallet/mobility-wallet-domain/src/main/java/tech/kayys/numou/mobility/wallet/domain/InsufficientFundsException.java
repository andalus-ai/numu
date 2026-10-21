package tech.kayys.numou.mobility.wallet.domain;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

public final class InsufficientFundsException extends BusinessRuleViolation {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
