package tech.kayys.syirkah.project.domain.project;

import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;

public final class InvalidProjectStateException
        extends InvalidStateException {

    public InvalidProjectStateException(String message) {
        super(message);
    }
}