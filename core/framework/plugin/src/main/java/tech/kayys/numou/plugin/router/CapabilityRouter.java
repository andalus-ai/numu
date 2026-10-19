package tech.kayys.numou.plugin.router;

import tech.kayys.numou.plugin.api.Capability;
import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.registry.CapabilityRegistry;

import java.util.List;
import java.util.Optional;

/**
 * Routes invocation and resolutions to plugin capabilities.
 */
public class CapabilityRouter {

    private final CapabilityRegistry registry;

    public CapabilityRouter(CapabilityRegistry registry) {
        this.registry = registry;
    }

    public Optional<NumouPlugin> route(Capability capability) {
        List<NumouPlugin> providers = registry.getProviders(capability);
        if (providers.isEmpty()) {
            return Optional.empty();
        }
        // Returns the primary (first active) provider
        return Optional.of(providers.get(0));
    }

    public Optional<NumouPlugin> route(String capabilityName) {
        List<NumouPlugin> providers = registry.getProviders(capabilityName);
        if (providers.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(providers.get(0));
    }

    public List<NumouPlugin> routeAll(Capability capability) {
        return registry.getProviders(capability);
    }

    public List<NumouPlugin> routeAll(String capabilityName) {
        return registry.getProviders(capabilityName);
    }
}
