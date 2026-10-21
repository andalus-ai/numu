package tech.kayys.numou.mobility.ride.domain;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

public final class ActiveRideExistsException extends BusinessRuleViolation {
    public ActiveRideExistsException() {
        super("You already have a ride in progress");
    }
}
