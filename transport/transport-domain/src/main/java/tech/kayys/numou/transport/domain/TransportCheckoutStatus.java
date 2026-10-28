package tech.kayys.numou.transport.domain;

/** Durable state of the commercial checkout orchestration; distinct from hold and payment state. */
public enum TransportCheckoutStatus {
    REQUESTED,
    HOLD_CREATED,
    PAYMENT_PENDING,
    PAYMENT_ACTION_REQUIRED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    CANCELLED,
    RECONCILIATION_REQUIRED
}
