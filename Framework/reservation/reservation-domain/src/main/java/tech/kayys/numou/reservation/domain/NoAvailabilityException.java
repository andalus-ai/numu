package tech.kayys.numou.reservation.domain;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

/**
 * Thrown when the availability ledger cannot allocate contiguous resources for the requested capacity and time range.
 */
public class NoAvailabilityException extends BusinessRuleViolation {
    public NoAvailabilityException(String message) {
        super(message);
    }
}
