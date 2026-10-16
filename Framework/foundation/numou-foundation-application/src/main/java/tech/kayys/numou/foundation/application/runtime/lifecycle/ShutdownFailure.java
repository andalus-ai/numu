package tech.kayys.numou.foundation.application.runtime.lifecycle;

import java.util.Objects;

/**
 * Details of an individual component failure encountered during shutdown.
 */
public record ShutdownFailure(
        ShutdownPhase phase,
        String componentId,
        String message,
        Throwable cause
) {
    public ShutdownFailure {
        Objects.requireNonNull(phase, "phase cannot be null");
        Objects.requireNonNull(componentId, "componentId cannot be null");
        message = message != null ? message : "";
    }
}
