package tech.kayys.numou.courier.domain;

/** Lifecycle of a reusable physical transport container. */
public enum TransportContainerStatus {
    OPEN,
    SEALED,
    DISPATCHED,
    RECEIVED,
    CANCELLED
}
