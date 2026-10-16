package tech.kayys.syirkah.identity.domain.exception;

import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;

public final class UserAlreadyActiveException extends InvalidStateException {

    public UserAlreadyActiveException() {
        super("User is already active");
    }

}
