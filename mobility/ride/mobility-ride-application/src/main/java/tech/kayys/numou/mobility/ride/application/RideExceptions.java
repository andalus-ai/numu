package tech.kayys.numou.mobility.ride.application;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

/** Application-level ride failures. */
public final class RideExceptions {

    private RideExceptions() { }

    /** Wrong pickup PIN. The failed attempt has already been saved. */
    public static final class WrongPinException extends BusinessRuleViolation {
        private final int attemptsLeft;

        public WrongPinException(int attemptsLeft) {
            super("Incorrect PIN. " + attemptsLeft + " attempt(s) left.");
            this.attemptsLeft = attemptsLeft;
        }

        public int attemptsLeft() { return attemptsLeft; }
    }

    public static final class PinLockedException extends BusinessRuleViolation {
        public PinLockedException() {
            super("Too many wrong PIN attempts. Contact support.");
        }
    }

    public static final class OutsideServiceAreaException extends BusinessRuleViolation {
        public OutsideServiceAreaException() {
            super("We do not operate at that pickup location yet");
        }
    }
}
