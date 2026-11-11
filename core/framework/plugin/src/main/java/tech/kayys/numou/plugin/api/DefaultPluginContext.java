package tech.kayys.numou.plugin.api;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Default plugin context with optional application-shared configuration and service registries. */
public class DefaultPluginContext implements PluginContext {

    private final String pluginId;
    private final Map<String, String> properties;
    private final Map<Class<?>, Object> services;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public DefaultPluginContext(String pluginId) {
        this(pluginId, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    public DefaultPluginContext(String pluginId, Map<String, String> initialProperties) {
        this(pluginId, new ConcurrentHashMap<>(initialProperties == null ? Map.of() : initialProperties),
                new ConcurrentHashMap<>());
    }

    /**
     * Creates a context backed by caller-owned maps. Hosts may share the property and service maps
     * across plugin contexts while each context retains its own attributes.
     */
    public DefaultPluginContext(String pluginId, Map<String, String> properties,
            Map<Class<?>, Object> services) {
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("Plugin ID is required");
        }
        this.pluginId = pluginId;
        this.properties = java.util.Objects.requireNonNull(properties, "Properties map is required");
        this.services = java.util.Objects.requireNonNull(services, "Services map is required");
    }

    @Override
    public String pluginId() {
        return pluginId;
    }

    @Override
    public Optional<String> getProperty(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(properties.get(key));
    }

    public void setProperty(String key, String value) {
        if (key != null && !key.isBlank()) {
            if (value == null) {
                properties.remove(key);
            } else {
                properties.put(key, value);
            }
        }
    }

    @Override
    public <T> Optional<T> getService(Class<T> serviceType) {
        if (serviceType == null) {
            return Optional.empty();
        }
        Object service = services.get(serviceType);
        return serviceType.isInstance(service) ? Optional.of(serviceType.cast(service)) : Optional.empty();
    }

    @Override
    public <T> void registerService(Class<T> serviceType, T instance) {
        if (serviceType == null || instance == null || !serviceType.isInstance(instance)) {
            throw new IllegalArgumentException("A matching service type and instance are required");
        }
        services.put(serviceType, instance);
    }

    @Override
    public Map<String, Object> attributes() {
        return Map.copyOf(attributes);
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (key != null && !key.isBlank()) {
            if (value == null) {
                attributes.remove(key);
            } else {
                attributes.put(key, value);
            }
        }
    }
}
