package tech.kayys.numou.commerce.marketplace.application;

/** An idempotency key was reused for a semantically different order command. */
public final class MarketplaceOrderIdempotencyConflictException extends RuntimeException {
    public MarketplaceOrderIdempotencyConflictException(String message) {
        super(message);
    }
}
