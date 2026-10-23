package tech.kayys.numou.fulfillment.application;

public final class FulfillmentConcurrencyException extends RuntimeException {
    public FulfillmentConcurrencyException(String message) {
        super(message);
    }
}
