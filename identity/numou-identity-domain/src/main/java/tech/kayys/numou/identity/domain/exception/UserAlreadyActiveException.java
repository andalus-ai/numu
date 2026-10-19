package tech.kayys.numou.identity.domain.exception;

import tech.kayys.numou.foundation.domain.exception.InvalidStateException;

public final class UserAlreadyActiveException extends InvalidStateException {

    public UserAlreadyActiveException() {
        super("User is already active");
    }

}
