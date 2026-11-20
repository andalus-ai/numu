package tech.kayys.numou.plugin.application.runtime;

import java.time.Clock;
import java.util.Objects;

/** Internal best-effort event dispatch shared by startup and session shutdown. */
final class ApplicationRuntimeLifecycleEvents {
    private ApplicationRuntimeLifecycleEvents() { }

    static void emit(ApplicationRuntimeLifecycleObserver observer, Clock clock,
            String applicationId, String providerId, String pluginId,
            ApplicationRuntimeLifecycleEvent.Type type, String detail) {
        try {
            observer.onEvent(new ApplicationRuntimeLifecycleEvent(applicationId, providerId, pluginId,
                    type, clock.instant(), detail));
        } catch (RuntimeException ignored) {
            // Telemetry and host integrations must not change application lifecycle semantics.
        }
    }

    static ApplicationRuntimeLifecycleObserver observerOrNoop(
            ApplicationRuntimeLifecycleObserver observer) {
        return Objects.requireNonNullElse(observer, ApplicationRuntimeLifecycleObserver.NOOP);
    }
}
