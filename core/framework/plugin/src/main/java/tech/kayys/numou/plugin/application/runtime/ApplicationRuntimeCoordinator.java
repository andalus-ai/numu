package tech.kayys.numou.plugin.application.runtime;

import tech.kayys.numou.plugin.api.Capability;
import tech.kayys.numou.plugin.api.PluginContext;
import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Binds a resolved capability composition to concrete plugin implementations and executes it.
 *
 * Provider IDs are deployment/composition identities. Plugin IDs and plugin manifest versions
 * remain independent; the advertised capability contract is checked against the resolved provider.
 */
public final class ApplicationRuntimeCoordinator {
    private final Clock clock;

    public ApplicationRuntimeCoordinator() {
        this(Clock.systemUTC());
    }

    /** Clock injection makes lifecycle event timestamps deterministic in tests and host runtimes. */
    public ApplicationRuntimeCoordinator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Runtime clock is required");
    }

    public ApplicationRuntimeSession start(ApplicationComposition composition,
            Map<String, ? extends NumouPlugin> implementations) {
        return start(composition, implementations, new DefaultApplicationPluginContextFactory(),
                ApplicationRuntimeLifecycleObserver.NOOP);
    }

    public ApplicationRuntimeSession start(ApplicationComposition composition,
            Map<String, ? extends NumouPlugin> implementations,
            ApplicationPluginContextFactory contextFactory) {
        return start(composition, implementations, contextFactory, ApplicationRuntimeLifecycleObserver.NOOP);
    }

    /**
     * Starts the composition with host contexts and a best-effort lifecycle observer.
     * Observer RuntimeExceptions are isolated and cannot affect startup or rollback.
     */
    public ApplicationRuntimeSession start(ApplicationComposition composition,
            Map<String, ? extends NumouPlugin> implementations,
            ApplicationPluginContextFactory contextFactory,
            ApplicationRuntimeLifecycleObserver lifecycleObserver) {
        Objects.requireNonNull(composition, "Application composition is required");
        Objects.requireNonNull(implementations, "Runtime implementation bindings are required");
        Objects.requireNonNull(contextFactory, "Application plugin context factory is required");
        ApplicationRuntimeLifecycleObserver observer =
                ApplicationRuntimeLifecycleEvents.observerOrNoop(lifecycleObserver);

        List<BoundPlugin> ordered = preflight(composition, implementations);
        ApplicationPluginContextFactory applicationContextFactory =
                contextFactory.forApplication(composition.applicationId());
        if (applicationContextFactory == null) {
            throw new ApplicationRuntimeException("Application context factory returned null for "
                    + composition.applicationId());
        }
        ApplicationRuntimeLifecycleEvents.emit(observer, clock, composition.applicationId(), null, null,
                ApplicationRuntimeLifecycleEvent.Type.APPLICATION_STARTING, null);
        List<BoundPlugin> attempted = new ArrayList<>();
        Map<String, PluginContext> contexts = new java.util.LinkedHashMap<>();
        Map<String, NumouPlugin> pluginsByProvider = new java.util.LinkedHashMap<>();
        Map<String, String> providerByCapability = new java.util.LinkedHashMap<>();
        String currentProvider = null;
        try {
            for (BoundPlugin binding : ordered) {
                currentProvider = binding.provider().providerId();
                ApplicationRuntimeLifecycleEvents.emit(observer, clock, composition.applicationId(),
                        currentProvider, binding.plugin().id(),
                        ApplicationRuntimeLifecycleEvent.Type.PROVIDER_INITIALIZATION_STARTED, null);
                PluginContext context = applicationContextFactory.create(binding.provider(), binding.plugin());
                if (context == null || !binding.plugin().id().equals(context.pluginId())) {
                    throw new IllegalStateException("Context factory must return a context for plugin "
                            + binding.plugin().id() + " (provider " + currentProvider + ")");
                }
                contexts.put(currentProvider, context);
                pluginsByProvider.put(currentProvider, binding.plugin());
                providerByCapability.put(binding.provider().capabilityId(), currentProvider);
                attempted.add(binding);
                binding.plugin().initialize(context);
                ApplicationRuntimeLifecycleEvents.emit(observer, clock, composition.applicationId(),
                        currentProvider, binding.plugin().id(),
                        ApplicationRuntimeLifecycleEvent.Type.PROVIDER_INITIALIZED, null);
            }
            for (BoundPlugin binding : ordered) {
                currentProvider = binding.provider().providerId();
                ApplicationRuntimeLifecycleEvents.emit(observer, clock, composition.applicationId(),
                        currentProvider, binding.plugin().id(),
                        ApplicationRuntimeLifecycleEvent.Type.PROVIDER_START_STARTED, null);
                binding.plugin().start();
                ApplicationRuntimeLifecycleEvents.emit(observer, clock, composition.applicationId(),
                        currentProvider, binding.plugin().id(),
                        ApplicationRuntimeLifecycleEvent.Type.PROVIDER_STARTED, null);
            }
            return new ApplicationRuntimeSession(composition.applicationId(),
                    ordered.stream().map(b -> b.provider().providerId()).toList(),
                    pluginsByProvider, contexts, providerByCapability, observer, clock);
        } catch (Exception failure) {
            ApplicationRuntimeLifecycleEvents.emit(observer, clock, composition.applicationId(),
                    currentProvider, currentPluginId(ordered, currentProvider),
                    ApplicationRuntimeLifecycleEvent.Type.STARTUP_FAILED,
                    failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()));
            ApplicationRuntimeException startupFailure = new ApplicationRuntimeException(
                    "Failed to start application " + composition.applicationId()
                            + " at provider " + currentProvider,
                    currentProvider, failure);
            rollback(attempted, startupFailure, composition.applicationId(), observer);
            throw startupFailure;
        }
    }

    private String currentPluginId(List<BoundPlugin> ordered, String providerId) {
        return ordered.stream().filter(binding -> binding.provider().providerId().equals(providerId))
                .map(binding -> binding.plugin().id()).findFirst().orElse(null);
    }

    private List<BoundPlugin> preflight(ApplicationComposition composition,
            Map<String, ? extends NumouPlugin> implementations) {
        List<String> diagnostics = new ArrayList<>();
        List<BoundPlugin> ordered = new ArrayList<>();
        Set<NumouPlugin> instances = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> pluginIds = new HashSet<>();

        for (CapabilityProvider provider : composition.initializationOrder()) {
            NumouPlugin plugin = implementations.get(provider.providerId());
            if (plugin == null) {
                diagnostics.add("Missing runtime implementation for provider " + provider.providerId()
                        + " (" + provider.capabilityId() + ")");
                continue;
            }
            if (!instances.add(plugin)) {
                diagnostics.add("Runtime plugin instance is bound to more than one provider; duplicate at "
                        + provider.providerId());
                continue;
            }
            if (plugin.id() == null || plugin.id().isBlank() || !pluginIds.add(plugin.id())) {
                diagnostics.add("Runtime plugin ID is blank or duplicated for provider " + provider.providerId());
                continue;
            }
            if (plugin.manifest() == null) {
                diagnostics.add("Runtime plugin " + plugin.id() + " has no manifest");
                continue;
            }
            List<Capability> matching = plugin.capabilities().stream()
                    .filter(capability -> capability.name().equals(provider.capabilityId()))
                    .toList();
            if (matching.size() != 1) {
                diagnostics.add("Runtime plugin " + plugin.id() + " must advertise exactly one "
                        + provider.capabilityId() + " capability; found " + matching.size());
                continue;
            }
            String advertisedVersion = matching.get(0).version();
            if (!provider.contractVersion().equals(advertisedVersion)) {
                diagnostics.add("Runtime plugin " + plugin.id() + " advertises "
                        + provider.capabilityId() + "@" + advertisedVersion
                        + " but composition selected contract version " + provider.contractVersion());
                continue;
            }
            ordered.add(new BoundPlugin(provider, plugin));
        }

        if (!diagnostics.isEmpty()) {
            throw new ApplicationRuntimeException(String.join("; ", diagnostics));
        }
        return List.copyOf(ordered);
    }

    private void rollback(Collection<BoundPlugin> attempted, ApplicationRuntimeException failure,
            String applicationId, ApplicationRuntimeLifecycleObserver observer) {
        List<BoundPlugin> reverse = new ArrayList<>(attempted);
        ApplicationRuntimeLifecycleEvents.emit(observer, clock, applicationId, null, null,
                ApplicationRuntimeLifecycleEvent.Type.ROLLBACK_STARTED, null);
        for (int i = reverse.size() - 1; i >= 0; i--) {
            BoundPlugin binding = reverse.get(i);
            try {
                // Include the plugin whose initialize call failed: it may have allocated partial resources.
                binding.plugin().stop();
                ApplicationRuntimeLifecycleEvents.emit(observer, clock, applicationId,
                        binding.provider().providerId(), binding.plugin().id(),
                        ApplicationRuntimeLifecycleEvent.Type.PROVIDER_ROLLBACK_COMPLETED, null);
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
                ApplicationRuntimeLifecycleEvents.emit(observer, clock, applicationId,
                        binding.provider().providerId(), binding.plugin().id(),
                        ApplicationRuntimeLifecycleEvent.Type.PROVIDER_ROLLBACK_FAILED,
                        cleanupFailure.getClass().getSimpleName() + ": "
                                + String.valueOf(cleanupFailure.getMessage()));
            }
        }
        ApplicationRuntimeLifecycleEvents.emit(observer, clock, applicationId, null, null,
                ApplicationRuntimeLifecycleEvent.Type.ROLLBACK_COMPLETED,
                failure.getSuppressed().length == 0 ? null : failure.getSuppressed().length + " cleanup failure(s)");
    }

    private record BoundPlugin(CapabilityProvider provider, NumouPlugin plugin) {
    }
}
