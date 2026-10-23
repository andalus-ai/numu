package tech.kayys.numou.fulfillment.domain;

/** Explicit lifecycle states for an industry-neutral fulfillment order. */
public enum FulfillmentStatus {
    REQUESTED,
    PARTIALLY_ALLOCATED,
    ALLOCATED,
    PARTIALLY_PICKED,
    PICKED,
    PARTIALLY_PACKED,
    PACKED,
    STAGED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REJECTED
}
