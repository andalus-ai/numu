package tech.kayys.syirkah.identity.domain.exception;

import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;

public final class UserAlreadyDeactivatedException extends InvalidStateException {

    public UserAlreadyDeactivatedException() {
        super("User is already deactivated");
    }

}
