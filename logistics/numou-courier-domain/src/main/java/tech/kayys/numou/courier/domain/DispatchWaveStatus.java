package tech.kayys.numou.courier.domain;

/** Lifecycle of a sortation and dispatch wave. */
public enum DispatchWaveStatus {
    PLANNED,
    LOADING,
    READY,
    DEPARTED,
    CANCELLED
}
