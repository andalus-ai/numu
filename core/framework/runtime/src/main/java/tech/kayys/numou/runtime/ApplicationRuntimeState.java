package tech.kayys.numou.runtime;

/** Observable lifecycle states of one application host instance. */
public enum ApplicationRuntimeState {
    NEW,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED
}
