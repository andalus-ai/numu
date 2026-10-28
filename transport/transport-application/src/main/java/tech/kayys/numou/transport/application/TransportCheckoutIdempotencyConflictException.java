package tech.kayys.numou.transport.application;

/** Idempotency key was reused for a semantically different checkout command. */
public final class TransportCheckoutIdempotencyConflictException extends IllegalArgumentException {
    public TransportCheckoutIdempotencyConflictException(String message) {
        super(message);
    }
}
