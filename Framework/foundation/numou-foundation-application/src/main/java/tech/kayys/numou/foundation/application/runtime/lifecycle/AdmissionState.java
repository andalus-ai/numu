package tech.kayys.numou.foundation.application.runtime.lifecycle;

/**
 * Lifecycle states of request/work admission (config02.md §P4-08 #4).
 */
public enum AdmissionState {
    CLOSED,
    OPEN,
    DRAINING
}
