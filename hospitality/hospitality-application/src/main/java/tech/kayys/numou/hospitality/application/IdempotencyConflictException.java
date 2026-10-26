package tech.kayys.numou.hospitality.application;

public final class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("Idempotency key was already used for a different booking request");
    }
}
