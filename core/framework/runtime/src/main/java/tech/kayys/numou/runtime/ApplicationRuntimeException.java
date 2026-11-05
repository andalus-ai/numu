package tech.kayys.numou.runtime;

/** Failure while composing, starting, or stopping an application runtime. */
public final class ApplicationRuntimeException extends RuntimeException {
    public ApplicationRuntimeException(String message) {
        super(message);
    }

    public ApplicationRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
