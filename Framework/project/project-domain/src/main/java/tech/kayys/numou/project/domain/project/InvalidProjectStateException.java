package tech.kayys.numou.project.domain.project;

import tech.kayys.numou.foundation.domain.exception.InvalidStateException;

public final class InvalidProjectStateException
        extends InvalidStateException {

    public InvalidProjectStateException(String message) {
        super(message);
    }
}