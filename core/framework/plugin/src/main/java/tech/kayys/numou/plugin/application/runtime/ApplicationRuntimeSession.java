package tech.kayys.numou.plugin.application.runtime;

import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.api.PluginContext;
import tech.kayys.numou.plugin.api.PluginState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the plugins selected for one application composition.
 *
 * Routing is deliberately application-scoped: this session never consults the global plugin
 * registry, so an unrelated or unselected plugin cannot become a fallback provider.
 */
public final class ApplicationRuntimeSession implements AutoCloseable {
    private final String applicationId;
    private final List<String> providerIds;
    private final Map<String, NumouPlugin> pluginsByProvider;
    private final Map<String, PluginContext> contexts;
    private final Map<String, String> providerByCapability;
    private final AtomicBoolean stopped = new AtomicBoolean();

    ApplicationRuntimeSession(String applicationId, List<String> providerIds,
            Map<String, NumouPlugin> pluginsByProvider, Map<String, PluginContext> contexts,
            Map<String, String> providerByCapability) {
        this.applicationId = applicationId;
        this.providerIds = List.copyOf(providerIds);
        this.pluginsByProvider = Map.copyOf(pluginsByProvider);
        this.contexts = Map.copyOf(contexts);
        this.providerByCapability = Map.copyOf(providerByCapability);
    }

    public String applicationId() { return applicationId; }

    public List<String> providerIds() { return providerIds; }

    public Optional<PluginContext> context(String providerId) {
        return Optional.ofNullable(contexts.get(providerId));
    }

    public Map<String, PluginContext> contexts() { return contexts; }

    /**
     * Resolve only the provider selected by this application's composition. A provider is
     * routable only while its plugin reports ACTIVE and the session has not been stopped.
     */
    public Optional<NumouPlugin> route(String capabilityId) {
        if (stopped.get() || capabilityId == null) return Optional.empty();
        String providerId = providerByCapability.get(capabilityId);
        NumouPlugin plugin = providerId == null ? null : pluginsByProvider.get(providerId);
        return plugin != null && plugin.state() == PluginState.ACTIVE
                ? Optional.of(plugin) : Optional.empty();
    }

    public boolean isStopped() { return stopped.get(); }

    /** Immutable point-in-time health view; readiness requires every selected plugin to be ACTIVE. */
    public ApplicationRuntimeHealth health() {
        List<ApplicationRuntimeProviderHealth> providers = new ArrayList<>();
        for (String providerId : providerIds) {
            NumouPlugin plugin = pluginsByProvider.get(providerId);
            PluginState state = plugin == null ? PluginState.FAILED : plugin.state();
            providers.add(new ApplicationRuntimeProviderHealth(providerId,
                    plugin == null ? null : plugin.id(), state,
                    !stopped.get() && state == PluginState.ACTIVE));
        }
        boolean ready = !stopped.get() && !providers.isEmpty()
                && providers.stream().allMatch(ApplicationRuntimeProviderHealth::ready);
        return new ApplicationRuntimeHealth(applicationId, ready, stopped.get(), providers);
    }

    @Override public void close() { stop(); }

    public void stop() {
        if (!stopped.compareAndSet(false, true)) return;
        List<Throwable> failures = new ArrayList<>();
        for (int i = providerIds.size() - 1; i >= 0; i--) {
            NumouPlugin plugin = pluginsByProvider.get(providerIds.get(i));
            if (plugin == null) continue;
            try {
                plugin.stop();
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
