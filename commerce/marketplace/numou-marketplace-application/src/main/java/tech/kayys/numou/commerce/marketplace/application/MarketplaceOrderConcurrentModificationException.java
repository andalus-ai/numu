package tech.kayys.numou.commerce.marketplace.application;

/** Raised when an order transition loses a persistence compare-and-set race. */
public final class MarketplaceOrderConcurrentModificationException extends RuntimeException {
    public MarketplaceOrderConcurrentModificationException(String message) {
        super(message);
    }
}