package tech.kayys.numou.commerce.marketplace.application.checkout;

/** Durable orchestration state; payment and inventory remain owned by their capabilities. */
public enum MarketplaceCheckoutStatus {
    RESERVING_INVENTORY,
    AWAITING_PAYMENT,
    READY_FOR_FULFILLMENT,
    COMPENSATION_REQUIRED,
    RECONCILIATION_REQUIRED,
    FAILED
}
