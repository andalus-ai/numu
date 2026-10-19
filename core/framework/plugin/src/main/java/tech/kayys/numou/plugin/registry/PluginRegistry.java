package tech.kayys.numou.plugin.registry;

import tech.kayys.numou.plugin.api.Capability;
import tech.kayys.numou.plugin.api.NumouPlugin;

import java.util.List;
import java.util.Optional;

/**
 * Registry interface for managing loaded Numou plugins.
 */
public interface PluginRegistry {

    void register(NumouPlugin plugin);

    void unregister(String pluginId);

    Optional<NumouPlugin> get(String pluginId);

    List<NumouPlugin> all();

    List<NumouPlugin> findByCapability(Capability capability);

    int size();

    void clear();
}
