package tech.kayys.numou.fulfillment.application;

public final class FulfillmentIdempotencyConflictException extends RuntimeException {
    public FulfillmentIdempotencyConflictException(String message) {
        super(message);
    }
}
