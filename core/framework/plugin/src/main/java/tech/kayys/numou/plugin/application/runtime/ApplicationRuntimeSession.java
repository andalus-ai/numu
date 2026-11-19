package tech.kayys.numou.plugin.application.runtime;

import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.api.PluginContext;
import tech.kayys.numou.plugin.api.PluginState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

/** Owns one application's selected plugins, routing boundary and shutdown lifecycle. */
public final class ApplicationRuntimeSession implements AutoCloseable {
    private final String applicationId;
    private final List<String> providerIds;
    private final Map<String, NumouPlugin> pluginsByProvider;
    private final Map<String, PluginContext> contexts;
    private final Map<String, String> providerByCapability;
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final Set<String> stoppedProviders = new HashSet<>();
    private final Map<String, String> stopFailures = new ConcurrentHashMap<>();

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

    /** Resolve only this application's selected provider, and only while it remains ACTIVE. */
    public Optional<NumouPlugin> route(String capabilityId) {
        if (stopRequested.get() || capabilityId == null) return Optional.empty();
        String providerId = providerByCapability.get(capabilityId);
        NumouPlugin plugin = providerId == null ? null : pluginsByProvider.get(providerId);
        return plugin != null && plugin.state() == PluginState.ACTIVE
                ? Optional.of(plugin) : Optional.empty();
    }

    public boolean isStopped() { return stopped.get(); }
    public boolean isStopping() { return stopRequested.get() && !stopped.get(); }

    /** Point-in-time readiness; stopping sessions are immediately unready. */
    public ApplicationRuntimeHealth health() {
        List<ApplicationRuntimeProviderHealth> providers = new ArrayList<>();
        for (String providerId : providerIds) {
            NumouPlugin plugin = pluginsByProvider.get(providerId);
            PluginState state = plugin == null ? PluginState.FAILED : plugin.state();
            providers.add(new ApplicationRuntimeProviderHealth(providerId,
                    plugin == null ? null : plugin.id(), state,
                    !stopRequested.get() && state == PluginState.ACTIVE, stopFailures.get(providerId)));
        }
        boolean ready = !stopRequested.get() && !providers.isEmpty()
                && providers.stream().allMatch(ApplicationRuntimeProviderHealth::ready);
        return new ApplicationRuntimeHealth(applicationId, ready, stopped.get(), providers);
    }

    @Override public void close() { stop(); }

    /**
     * Stops in reverse initialization order. Successfully stopped providers are remembered;
     * failed stops remain eligible for a later retry. Routing is disabled on the first attempt.
     */
    public synchronized void stop() {
        if (stopped.get()) return;
        stopRequested.set(true);
        List<Throwable> failures = new ArrayList<>();
        for (int i = providerIds.size() - 1; i >= 0; i--) {
            String providerId = providerIds.get(i);
            if (stoppedProviders.contains(providerId)) continue;
            NumouPlugin plugin = pluginsByProvider.get(providerId);
            if (plugin == null) {
                stoppedProviders.add(providerId);
                continue;
            }
            try {
                plugin.stop();
                stoppedProviders.add(providerId);
                stopFailures.remove(providerId);
            } catch (Exception failure) {
                stopFailures.put(providerId, failure.getClass().getSimpleName() + ": "
                        + String.valueOf(failure.getMessage()));
                failures.add(failure);
            }
        }
        if (stoppedProviders.size() == providerIds.size()) {
            stopped.set(true);
        }
        if (!failures.isEmpty()) {
            ApplicationRuntimeException exception = new ApplicationRuntimeException(
                    "Failed to stop " + failures.size() + " plugin(s) for application " + applicationId
                            + "; " + stoppedProviders.size() + "/" + providerIds.size()
                            + " providers stopped");
            failures.forEach(exception::addSuppressed);
            throw exception;
        }
    }
}
