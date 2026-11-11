package tech.kayys.numou.plugin.application.runtime;

import tech.kayys.numou.plugin.api.Capability;
import tech.kayys.numou.plugin.api.PluginContext;
import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

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

    /**
     * Starts one application. The map key is the CapabilityProvider.providerId, not plugin.id().
     * All bindings are validated before any plugin lifecycle method is invoked.
     */
    public ApplicationRuntimeSession start(ApplicationComposition composition,
            Map<String, ? extends NumouPlugin> implementations) {
        return start(composition, implementations, new DefaultApplicationPluginContextFactory());
    }

    /**
     * Starts the composition using host-supplied plugin contexts. The factory is invoked only
     * after every provider binding has passed preflight validation.
     */
    public ApplicationRuntimeSession start(ApplicationComposition composition,
            Map<String, ? extends NumouPlugin> implementations,
            ApplicationPluginContextFactory contextFactory) {
        Objects.requireNonNull(composition, "Application composition is required");
        Objects.requireNonNull(implementations, "Runtime implementation bindings are required");
        Objects.requireNonNull(contextFactory, "Application plugin context factory is required");

        List<BoundPlugin> ordered = preflight(composition, implementations);
        List<BoundPlugin> attempted = new ArrayList<>();
        Map<String, PluginContext> contexts = new java.util.LinkedHashMap<>();
        String currentProvider = null;
        try {
            for (BoundPlugin binding : ordered) {
                currentProvider = binding.provider().providerId();
                PluginContext context = contextFactory.create(binding.provider(), binding.plugin());
                if (context == null || !binding.plugin().id().equals(context.pluginId())) {
                    throw new IllegalStateException("Context factory must return a context for plugin "
                            + binding.plugin().id() + " (provider " + currentProvider + ")");
                }
                contexts.put(currentProvider, context);
                attempted.add(binding);
                binding.plugin().initialize(context);
            }
            for (BoundPlugin binding : ordered) {
                currentProvider = binding.provider().providerId();
                binding.plugin().start();
            }
            return new ApplicationRuntimeSession(composition.applicationId(),
                    ordered.stream().map(b -> b.provider().providerId()).toList(),
                    attempted.stream().map(BoundPlugin::plugin).toList(), contexts);
        } catch (Exception failure) {
            ApplicationRuntimeException startupFailure = new ApplicationRuntimeException(
                    "Failed to start application " + composition.applicationId()
                            + " at provider " + currentProvider,
                    currentProvider, failure);
            rollback(attempted, startupFailure);
            throw startupFailure;
        }
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

    private void rollback(Collection<BoundPlugin> attempted, ApplicationRuntimeException failure) {
        List<BoundPlugin> reverse = new ArrayList<>(attempted);
        for (int i = reverse.size() - 1; i >= 0; i--) {
            try {
                // Include the plugin whose initialize call failed: it may have allocated partial resources.
                reverse.get(i).plugin().stop();
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private record BoundPlugin(CapabilityProvider provider, NumouPlugin plugin) {
    }
}
