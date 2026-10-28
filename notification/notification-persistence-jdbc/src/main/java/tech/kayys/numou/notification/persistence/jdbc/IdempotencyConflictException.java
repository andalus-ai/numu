package tech.kayys.numou.notification.persistence.jdbc;

/** Signals reuse of an idempotency key with a different logical request. */
public final class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}