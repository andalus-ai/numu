package tech.kayys.syirkah.project.domain.milestone;

import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;

/** Raised when a milestone transition is attempted from the wrong status. */
public final class InvalidMilestoneStateException
        extends InvalidStateException {

    public InvalidMilestoneStateException(String message) {
        super(message);
    }
}
