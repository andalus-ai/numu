package tech.kayys.syirkah.plugin.registry;

import tech.kayys.syirkah.plugin.api.Capability;
import tech.kayys.syirkah.plugin.api.SyirkahPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class DefaultCapabilityRegistry implements CapabilityRegistry {

    private final Map<String, List<SyirkahPlugin>> capabilityMap = new ConcurrentHashMap<>();
    private final Map<String, Capability> capabilityMetadata = new ConcurrentHashMap<>();

    @Override
    public void register(Capability capability, SyirkahPlugin plugin) {
        if (capability == null || plugin == null) {
            return;
        }
        capabilityMetadata.put(capability.name().toUpperCase(), capability);
        capabilityMap.computeIfAbsent(capability.name().toUpperCase(), k -> new CopyOnWriteArrayList<>())
                .add(plugin);
    }

    @Override
    public void unregister(Capability capability, String pluginId) {
        if (capability == null || pluginId == null) {
            return;
        }
        List<SyirkahPlugin> list = capabilityMap.get(capability.name().toUpperCase());
        if (list != null) {
            list.removeIf(p -> p.id().equals(pluginId));
        }
    }

    @Override
    public List<SyirkahPlugin> getProviders(Capability capability) {
        if (capability == null) {
            return List.of();
        }
        return getProviders(capability.name());
    }

    @Override
    public List<SyirkahPlugin> getProviders(String capabilityName) {
        if (capabilityName == null) {
            return List.of();
        }
        List<SyirkahPlugin> providers = capabilityMap.get(capabilityName.toUpperCase());
        return providers == null ? List.of() : List.copyOf(providers);
    }

    @Override
    public Set<Capability> allCapabilities() {
        return Set.copyOf(capabilityMetadata.values());
    }

    @Override
    public void clear() {
        capabilityMap.clear();
        capabilityMetadata.clear();
    }
}
