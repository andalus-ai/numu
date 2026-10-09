package tech.kayys.syirkah.plugin.registry;

import tech.kayys.syirkah.plugin.api.Capability;
import tech.kayys.syirkah.plugin.api.SyirkahPlugin;

import java.util.List;
import java.util.Optional;

/**
 * Registry interface for managing loaded Syirkah plugins.
 */
public interface PluginRegistry {

    void register(SyirkahPlugin plugin);

    void unregister(String pluginId);

    Optional<SyirkahPlugin> get(String pluginId);

    List<SyirkahPlugin> all();

    List<SyirkahPlugin> findByCapability(Capability capability);

    int size();

    void clear();
}
