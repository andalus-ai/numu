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
    private final Map<String, Object> contextAttributes;
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public DefaultApplicationPluginContextFactory() {
        this(Map.of());
    }

    public DefaultApplicationPluginContextFactory(Map<String, String> properties) {
        this(properties, Map.of());
    }

    /**
     * @param properties application configuration shared by all selected plugins
     * @param contextAttributes host context such as tenant, actor and correlation identifiers;
     *                          copied into each plugin's isolated attribute map
     */
    public DefaultApplicationPluginContextFactory(Map<String, String> properties,
            Map<String, Object> contextAttributes) {
        this.properties = new ConcurrentHashMap<>(properties == null ? Map.of() : properties);
        this.contextAttributes = Map.copyOf(contextAttributes == null ? Map.of() : contextAttributes);
    }

    @Override
    public PluginContext create(CapabilityProvider provider, NumouPlugin plugin) {
        DefaultPluginContext context = new DefaultPluginContext(plugin.id(), properties, services);
        contextAttributes.forEach(context::setAttribute);
        return context;
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
