package tech.kayys.numou.foundation.application.runtime.lifecycle;

/**
 * Deterministic phases of the graceful shutdown sequence (config02.md §P4-08 #10).
 */
public enum ShutdownPhase {
    AVAILABILITY,
    ADMISSION,
    SCHEDULERS,
    CONSUMERS,
    DISPATCHERS,
    DRAIN,
    MODULES,
    INFRASTRUCTURE,
    FINALIZATION
}
