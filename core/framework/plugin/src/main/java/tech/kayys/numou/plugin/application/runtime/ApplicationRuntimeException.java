package tech.kayys.numou.plugin.application.runtime;

/** Failure while binding or executing an application composition. */
public final class ApplicationRuntimeException extends RuntimeException {
    private final String providerId;

    public ApplicationRuntimeException(String message) {
        this(message, null, null);
    }

    public ApplicationRuntimeException(String message, String providerId, Throwable cause) {
        super(message, cause);
        this.providerId = providerId;
    }

    public String providerId() {
        return providerId;
    }
}
