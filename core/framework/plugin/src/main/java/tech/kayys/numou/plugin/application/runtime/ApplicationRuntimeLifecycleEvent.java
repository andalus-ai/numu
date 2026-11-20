package tech.kayys.numou.plugin.application.runtime;

import java.time.Instant;

/** A vendor-neutral observation of an application or provider lifecycle transition. */
public record ApplicationRuntimeLifecycleEvent(
        String applicationId,
        String providerId,
        String pluginId,
        Type type,
        Instant occurredAt,
        String detail) {

    public enum Type {
        APPLICATION_STARTING,
        PROVIDER_INITIALIZATION_STARTED,
        PROVIDER_INITIALIZED,
        PROVIDER_START_STARTED,
        PROVIDER_STARTED,
        STARTUP_FAILED,
        ROLLBACK_STARTED,
        PROVIDER_ROLLBACK_COMPLETED,
        PROVIDER_ROLLBACK_FAILED,
        ROLLBACK_COMPLETED,
        ROLLBACK_FAILED,
        SHUTDOWN_STARTED,
        PROVIDER_STOPPED,
        PROVIDER_STOP_FAILED,
        SHUTDOWN_COMPLETED,
        SHUTDOWN_FAILED
    }
}
