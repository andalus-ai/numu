package tech.kayys.syirkah.project.domain.phase;

import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;

/** Raised when a phase transition is attempted from the wrong status. */
public final class InvalidPhaseStateException
        extends InvalidStateException {

    public InvalidPhaseStateException(String message) {
        super(message);
    }
}
