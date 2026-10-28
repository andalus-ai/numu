package tech.kayys.numou.commerce.marketplace.domain;

/** Marketplace commercial lifecycle; payment, stock and shipment states remain externally owned. */
public enum MarketplaceOrderStatus {
    CREATED,
    SUBMITTED,
    SELLER_ACCEPTED,
    SELLER_REJECTED,
    FULFILLMENT_REQUESTED,
    CANCELLED,
    COMPLETED
}
