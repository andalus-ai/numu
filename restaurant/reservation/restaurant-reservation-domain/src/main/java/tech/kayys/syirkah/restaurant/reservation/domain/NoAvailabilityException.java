package tech.kayys.syirkah.restaurant.reservation.domain;

import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;

/** No table (or combination) can seat the party for the requested time (HTTP 409 with alternative slots). */
public final class NoAvailabilityException extends BusinessRuleViolation {

    public NoAvailabilityException(String message) {
        super(message);
    }
}
