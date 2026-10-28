package tech.kayys.numou.transport.spi;

/** A checkout changed concurrently; reload it and re-evaluate the requested transition. */
public final class TransportCheckoutConcurrencyException extends RuntimeException {
    public TransportCheckoutConcurrencyException(String message) {
        super(message);
    }

    public TransportCheckoutConcurrencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
