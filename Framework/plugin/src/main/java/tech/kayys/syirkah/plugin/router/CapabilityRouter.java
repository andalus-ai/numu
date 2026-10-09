package tech.kayys.syirkah.plugin.router;

import tech.kayys.syirkah.plugin.api.Capability;
import tech.kayys.syirkah.plugin.api.SyirkahPlugin;
import tech.kayys.syirkah.plugin.registry.CapabilityRegistry;

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

    public Optional<SyirkahPlugin> route(Capability capability) {
        List<SyirkahPlugin> providers = registry.getProviders(capability);
        if (providers.isEmpty()) {
            return Optional.empty();
        }
        // Returns the primary (first active) provider
        return Optional.of(providers.get(0));
    }

    public Optional<SyirkahPlugin> route(String capabilityName) {
        List<SyirkahPlugin> providers = registry.getProviders(capabilityName);
        if (providers.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(providers.get(0));
    }

    public List<SyirkahPlugin> routeAll(Capability capability) {
        return registry.getProviders(capability);
    }

    public List<SyirkahPlugin> routeAll(String capabilityName) {
        return registry.getProviders(capabilityName);
    }
}
