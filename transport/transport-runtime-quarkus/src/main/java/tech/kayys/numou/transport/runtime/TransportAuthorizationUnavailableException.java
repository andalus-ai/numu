package tech.kayys.numou.transport.runtime;

/** Signals that the configured security authority could not make a reliable decision. */
public final class TransportAuthorizationUnavailableException extends RuntimeException {
    public TransportAuthorizationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransportAuthorizationUnavailableException(String message) {
        super(message);
    }
}
