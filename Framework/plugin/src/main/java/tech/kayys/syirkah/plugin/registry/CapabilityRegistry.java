package tech.kayys.syirkah.plugin.registry;

import tech.kayys.syirkah.plugin.api.Capability;
import tech.kayys.syirkah.plugin.api.SyirkahPlugin;

import java.util.List;
import java.util.Set;

/**
 * Registry mapping capabilities to available plugins providing them.
 */
public interface CapabilityRegistry {

    void register(Capability capability, SyirkahPlugin plugin);

    void unregister(Capability capability, String pluginId);

    List<SyirkahPlugin> getProviders(Capability capability);

    List<SyirkahPlugin> getProviders(String capabilityName);

    Set<Capability> allCapabilities();

    void clear();
}
