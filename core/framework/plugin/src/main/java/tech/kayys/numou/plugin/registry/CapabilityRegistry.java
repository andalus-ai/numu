package tech.kayys.numou.plugin.registry;

import tech.kayys.numou.plugin.api.Capability;
import tech.kayys.numou.plugin.api.NumouPlugin;

import java.util.List;
import java.util.Set;

/**
 * Registry mapping capabilities to available plugins providing them.
 */
public interface CapabilityRegistry {

    void register(Capability capability, NumouPlugin plugin);

    void unregister(Capability capability, String pluginId);

    List<NumouPlugin> getProviders(Capability capability);

    List<NumouPlugin> getProviders(String capabilityName);

    Set<Capability> allCapabilities();

    void clear();
}
