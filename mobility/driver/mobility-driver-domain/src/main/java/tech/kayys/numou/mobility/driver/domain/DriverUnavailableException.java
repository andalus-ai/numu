package tech.kayys.numou.mobility.driver.domain;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

/** The driver cannot take a ride right now (offline, already on a trip, suspended, not approved). */
public final class DriverUnavailableException extends BusinessRuleViolation {

    public DriverUnavailableException(String message) {
        super(message);
    }
}
