package tech.kayys.syirkah.restaurant.kernel.domain;

import tech.kayys.syirkah.foundation.domain.exception.DomainException;

/**
 * Another writer changed the aggregate since it was loaded (HTTP 409). Safe to retry the whole
 * command: reload, re-apply the rule, save.
 */
public final class ConcurrencyConflictException extends DomainException {

    public ConcurrencyConflictException(String message) {
        super(message);
    }
}
