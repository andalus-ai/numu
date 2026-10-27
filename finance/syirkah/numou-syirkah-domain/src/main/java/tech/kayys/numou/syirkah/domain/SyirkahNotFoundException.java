package tech.kayys.numou.syirkah.domain;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

/**
 * A referenced syirkah, close or approval does not exist (for the caller's tenant). It is still a
 * {@link BusinessRuleViolation}, so existing handlers keep working; transports can map it to "not found".
 */
public class SyirkahNotFoundException extends BusinessRuleViolation {

    public SyirkahNotFoundException(String message) {
        super(message);
    }
}
