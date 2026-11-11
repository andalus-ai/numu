package tech.kayys.numou.plugin.application.runtime;

import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.api.PluginContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the successfully initialized plugins for one composed application.
 * Closing the session stops plugins in reverse composition order and is idempotent.
 */
public final class ApplicationRuntimeSession implements AutoCloseable {
    private final String applicationId;
    private final List<String> providerIds;
    private final List<NumouPlugin> initializedPlugins;
    private final java.util.Map<String, PluginContext> contexts;
    private final AtomicBoolean stopped = new AtomicBoolean();

    ApplicationRuntimeSession(String applicationId, List<String> providerIds,
            List<NumouPlugin> initializedPlugins, java.util.Map<String, PluginContext> contexts) {
        this.applicationId = applicationId;
        this.providerIds = List.copyOf(providerIds);
        this.initializedPlugins = List.copyOf(initializedPlugins);
        this.contexts = java.util.Map.copyOf(contexts);
    }

    public String applicationId() {
        return applicationId;
    }

    public List<String> providerIds() {
        return providerIds;
    }

    /** Returns the initialized plugin context keyed by composition provider ID. */
    public java.util.Optional<PluginContext> context(String providerId) {
        return java.util.Optional.ofNullable(contexts.get(providerId));
    }

    /** Immutable provider-to-context view for host diagnostics and lifecycle integrations. */
    public java.util.Map<String, PluginContext> contexts() {
        return contexts;
    }

    public boolean isStopped() {
        return stopped.get();
    }

    @Override
    public void close() {
        stop();
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        List<Throwable> failures = new ArrayList<>();
        for (int i = initializedPlugins.size() - 1; i >= 0; i--) {
            try {
                initializedPlugins.get(i).stop();
            } catch (Exception failure) {
                failures.add(failure);
            }
        }
        if (!failures.isEmpty()) {
            ApplicationRuntimeException exception = new ApplicationRuntimeException(
                    "Failed to stop " + failures.size() + " plugin(s) for application " + applicationId);
            failures.forEach(exception::addSuppressed);
            throw exception;
        }
    }
}
