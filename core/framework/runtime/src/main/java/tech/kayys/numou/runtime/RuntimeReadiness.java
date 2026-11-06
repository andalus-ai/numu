package tech.kayys.numou.runtime;

/** Deployment-facing readiness state for one application process. */
public enum RuntimeReadiness {
    STARTING,
    READY,
    STOPPING,
    STOPPED,
    FAILED
}
