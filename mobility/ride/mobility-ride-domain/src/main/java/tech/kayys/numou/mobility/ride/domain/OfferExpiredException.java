package tech.kayys.numou.mobility.ride.domain;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

/** The driver answered too late (or there was never an offer for them). */
public final class OfferExpiredException extends BusinessRuleViolation {
    public OfferExpiredException(String message) {
        super(message);
    }
}
