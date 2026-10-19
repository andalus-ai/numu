package tech.kayys.numou.foundation.application.runtime;

/**
 * Thrown when an illegal lifecycle state transition is attempted.
 */
public class LifecycleTransitionException extends ApplicationRuntimeException {

    public LifecycleTransitionException(String message) {
        super(message);
    }
}
