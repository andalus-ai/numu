package tech.kayys.numou.project.domain.phase;

import tech.kayys.numou.foundation.domain.exception.InvalidStateException;

/** Raised when a phase transition is attempted from the wrong status. */
public final class InvalidPhaseStateException
        extends InvalidStateException {

    public InvalidPhaseStateException(String message) {
        super(message);
    }
}
