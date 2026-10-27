package tech.kayys.payment.api;

/** Refund-operation state, distinct from the payment's own state. */
public enum RefundStatus {
    REQUESTED,
    PENDING,
    SUCCEEDED,
    FAILED,
    UNKNOWN
}