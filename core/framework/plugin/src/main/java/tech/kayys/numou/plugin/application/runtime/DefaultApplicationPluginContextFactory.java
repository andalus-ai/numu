package tech.kayys.numou.plugin.application.runtime;

import tech.kayys.numou.plugin.api.DefaultPluginContext;
import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.api.PluginContext;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-process host context. Configuration and typed services are shared across the
 * composed application; arbitrary attributes remain isolated to each plugin context.
 */
public final class DefaultApplicationPluginContextFactory implements ApplicationPluginContextFactory {
    private final Map<String, String> properties;
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public DefaultApplicationPluginContextFactory() {
        this(Map.of());
    }

    public DefaultApplicationPluginContextFactory(Map<String, String> properties) {
        this.properties = new ConcurrentHashMap<>(properties == null ? Map.of() : properties);
    }

    @Override
    public PluginContext create(CapabilityProvider provider, NumouPlugin plugin) {
        return new DefaultPluginContext(plugin.id(), properties, services);
    }

    /** Register a host-provided service before startup; all plugin contexts can resolve it. */
    public <T> void registerService(Class<T> serviceType, T instance) {
        if (serviceType == null || instance == null || !serviceType.isInstance(instance)) {
            throw new IllegalArgumentException("A matching service type and instance are required");
        }
        services.put(serviceType, instance);
    }

    /** Update an application property before or during startup. */
    public void setProperty(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Property key is required");
        }
        if (value == null) {
            properties.remove(key);
        } else {
            properties.put(key, value);
        }
    }
}
