package tech.kayys.numou.foundation.application.runtime;

/**
 * Lifecycle states of the Application Runtime (config01.md §P4-01 #8, §P4-05 #2).
 */
public enum ApplicationRuntimeState {
    NEW,
    CONFIGURING,
    VALIDATING,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED
}
