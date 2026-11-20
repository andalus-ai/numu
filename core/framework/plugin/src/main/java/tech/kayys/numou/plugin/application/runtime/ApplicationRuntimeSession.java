package tech.kayys.numou.plugin.application.runtime;

import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.api.PluginContext;
import tech.kayys.numou.plugin.api.PluginState;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

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
    private final ApplicationRuntimeLifecycleObserver lifecycleObserver;
    private final Clock clock;
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final Set<String> stoppedProviders = new HashSet<>();
    private final Map<String, String> stopFailures = new ConcurrentHashMap<>();

    ApplicationRuntimeSession(String applicationId, List<String> providerIds,
            Map<String, NumouPlugin> pluginsByProvider, Map<String, PluginContext> contexts,
            Map<String, String> providerByCapability) {
        this(applicationId, providerIds, pluginsByProvider, contexts, providerByCapability,
                ApplicationRuntimeLifecycleObserver.NOOP, Clock.systemUTC());
    }

    ApplicationRuntimeSession(String applicationId, List<String> providerIds,
            Map<String, NumouPlugin> pluginsByProvider, Map<String, PluginContext> contexts,
            Map<String, String> providerByCapability,
            ApplicationRuntimeLifecycleObserver lifecycleObserver, Clock clock) {
        this.applicationId = applicationId;
        this.providerIds = List.copyOf(providerIds);
        this.pluginsByProvider = Map.copyOf(pluginsByProvider);
        this.contexts = Map.copyOf(contexts);
        this.providerByCapability = Map.copyOf(providerByCapability);
        this.lifecycleObserver = ApplicationRuntimeLifecycleEvents.observerOrNoop(lifecycleObserver);
        this.clock = clock;
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
                    !stopRequested.get() && state == PluginState.ACTIVE, stopFailures.get(providerId)));
        }
        boolean ready = !stopped.get() && !providers.isEmpty()
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
        boolean retry = stopRequested.get();
        stopRequested.set(true);
        ApplicationRuntimeLifecycleEvents.emit(lifecycleObserver, clock, applicationId, null, null,
                ApplicationRuntimeLifecycleEvent.Type.SHUTDOWN_STARTED, retry ? "retry" : null);
        List<Throwable> failures = new ArrayList<>();
        for (int i = providerIds.size() - 1; i >= 0; i--) {
            NumouPlugin plugin = pluginsByProvider.get(providerIds.get(i));
            if (plugin == null) continue;
            try {
                plugin.stop();
                stoppedProviders.add(providerId);
                stopFailures.remove(providerId);
                ApplicationRuntimeLifecycleEvents.emit(lifecycleObserver, clock, applicationId,
                        providerId, plugin.id(), ApplicationRuntimeLifecycleEvent.Type.PROVIDER_STOPPED, null);
            } catch (Exception failure) {
                stopFailures.put(providerId, failure.getClass().getSimpleName() + ": "
                        + String.valueOf(failure.getMessage()));
                failures.add(failure);
                ApplicationRuntimeLifecycleEvents.emit(lifecycleObserver, clock, applicationId,
                        providerId, plugin.id(), ApplicationRuntimeLifecycleEvent.Type.PROVIDER_STOP_FAILED,
                        failure.getClass().getSimpleName());
            }
        }
        if (stoppedProviders.size() == providerIds.size()) {
            stopped.set(true);
        }
        if (!failures.isEmpty()) {
            ApplicationRuntimeLifecycleEvents.emit(lifecycleObserver, clock, applicationId, null, null,
                    ApplicationRuntimeLifecycleEvent.Type.SHUTDOWN_FAILED,
                    failures.size() + " provider stop failure(s)");
            ApplicationRuntimeException exception = new ApplicationRuntimeException(
                    "Failed to stop " + failures.size() + " plugin(s) for application " + applicationId
                            + "; " + stoppedProviders.size() + "/" + providerIds.size()
                            + " providers stopped");
            failures.forEach(exception::addSuppressed);
            throw exception;
        }
        ApplicationRuntimeLifecycleEvents.emit(lifecycleObserver, clock, applicationId, null, null,
                ApplicationRuntimeLifecycleEvent.Type.SHUTDOWN_COMPLETED, null);
    }
}
