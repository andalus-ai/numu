package tech.kayys.numou.plugin.application.runtime;

/**
 * Host extension point for metrics, tracing, audit, and operational event adapters.
 *
 * <p>Observers are best-effort: runtime lifecycle transitions are never failed or rolled back
 * because an observer throws a RuntimeException.</p>
 */
@FunctionalInterface
public interface ApplicationRuntimeLifecycleObserver {
    ApplicationRuntimeLifecycleObserver NOOP = event -> { };

    void onEvent(ApplicationRuntimeLifecycleEvent event);
}
