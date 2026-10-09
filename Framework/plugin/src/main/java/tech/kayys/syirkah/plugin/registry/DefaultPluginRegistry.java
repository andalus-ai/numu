package tech.kayys.syirkah.plugin.registry;

import tech.kayys.syirkah.plugin.api.Capability;
import tech.kayys.syirkah.plugin.api.SyirkahPlugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe default implementation of PluginRegistry.
 */
public class DefaultPluginRegistry implements PluginRegistry {

    private final Map<String, SyirkahPlugin> plugins = new ConcurrentHashMap<>();

    @Override
    public void register(SyirkahPlugin plugin) {
        if (plugin == null || plugin.id() == null) {
            throw new IllegalArgumentException("Plugin and plugin ID must not be null");
        }
        plugins.put(plugin.id(), plugin);
    }

    @Override
    public void unregister(String pluginId) {
        if (pluginId != null) {
            plugins.remove(pluginId);
        }
    }

    @Override
    public Optional<SyirkahPlugin> get(String pluginId) {
        if (pluginId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(plugins.get(pluginId));
    }

    @Override
    public List<SyirkahPlugin> all() {
        return List.copyOf(plugins.values());
    }

    @Override
    public List<SyirkahPlugin> findByCapability(Capability capability) {
        if (capability == null) {
            return List.of();
        }
        return plugins.values().stream()
                .filter(p -> p.capabilities().contains(capability) ||
                             p.capabilities().stream().anyMatch(c -> c.name().equalsIgnoreCase(capability.name())))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public int size() {
        return plugins.size();
    }

    @Override
    public void clear() {
        plugins.clear();
    }
}
