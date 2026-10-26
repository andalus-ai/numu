package tech.kayys.numou.transport.domain;

/** Lifecycle of a capacity claim on a scheduled departure's route segment. */
public enum PassengerHoldStatus {
    HELD,
    CONFIRMED,
    RELEASED,
    EXPIRED
}
