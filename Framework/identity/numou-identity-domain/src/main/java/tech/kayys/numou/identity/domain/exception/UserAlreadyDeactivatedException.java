package tech.kayys.numou.identity.domain.exception;

import tech.kayys.numou.foundation.domain.exception.InvalidStateException;

public final class UserAlreadyDeactivatedException extends InvalidStateException {

    public UserAlreadyDeactivatedException() {
        super("User is already deactivated");
    }

}
