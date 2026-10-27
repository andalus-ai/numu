package tech.kayys.payment.api;

/**
 * Provider-neutral payment lifecycle. PENDING and UNKNOWN are non-terminal;
 * callers must query/reconcile rather than infer failure from timeouts.
 */
public enum PaymentStatus {
    CREATED,
    REQUIRES_CUSTOMER_ACTION,
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    EXPIRED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    UNKNOWN
}