package tech.kayys.numou.transport.domain;

/** Payment capability refund-operation state; separate from checkout and payment status. */
public enum TransportCheckoutRefundStatus {
    REQUESTED,
    PENDING,
    SUCCEEDED,
    FAILED,
    UNKNOWN
}
