package tech.kayys.numou.plugin.application.runtime;

import tech.kayys.numou.plugin.api.DefaultPluginContext;
import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.api.PluginContext;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default in-process host context. Configuration and typed services are shared only by plugins
 * within the same application runtime; arbitrary attributes remain isolated per plugin.
 */
public final class DefaultApplicationPluginContextFactory implements ApplicationPluginContextFactory {
    private final Map<String, String> properties;
    private final Map<String, Object> contextAttributes;
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
    private final AtomicReference<String> tenantBoundApplication = new AtomicReference<>();

    public DefaultApplicationPluginContextFactory() {
        this(Map.of());
    }

    public DefaultApplicationPluginContextFactory(Map<String, String> properties) {
        this(properties, Map.of());
    }

    public DefaultApplicationPluginContextFactory(Map<String, String> properties,
            Map<String, Object> contextAttributes) {
        this.properties = new ConcurrentHashMap<>(properties == null ? Map.of() : properties);
        this.contextAttributes = Map.copyOf(contextAttributes == null ? Map.of() : contextAttributes);
    }

    @Override
    public ApplicationPluginContextFactory forApplication(String applicationId) {
        if (applicationId == null || applicationId.isBlank()) {
            throw new IllegalArgumentException("Application ID is required");
        }
        Object tenantId = contextAttributes.get("tenant.id");
        if (tenantId != null) {
            String bound = tenantBoundApplication.get();
            if (bound == null && tenantBoundApplication.compareAndSet(null, applicationId)) {
                bound = applicationId;
            } else if (bound == null) {
                bound = tenantBoundApplication.get();
            }
            if (!applicationId.equals(bound)) {
                throw new IllegalStateException("Tenant-bound context factory is already assigned to application "
                        + bound + "; create a separate factory for " + applicationId);
            }
        }
        Object suppliedApplicationId = contextAttributes.get("application.id");
        if (suppliedApplicationId != null && !applicationId.equals(suppliedApplicationId)) {
            throw new IllegalArgumentException("Context application.id does not match composition "
                    + applicationId);
        }
        Map<String, Object> scopedAttributes = new java.util.LinkedHashMap<>(contextAttributes);
        scopedAttributes.put("application.id", applicationId);
        DefaultApplicationPluginContextFactory scoped =
                new DefaultApplicationPluginContextFactory(properties, scopedAttributes);
        scoped.services.putAll(services);
        return scoped;
    }

    @Override
    public PluginContext create(CapabilityProvider provider, NumouPlugin plugin) {
        if (provider == null || plugin == null) {
            throw new IllegalArgumentException("Capability provider and plugin are required");
        }
        DefaultPluginContext context = new DefaultPluginContext(plugin.id(), properties, services);
        contextAttributes.forEach(context::setAttribute);
        return context;
    }

    /** Register a host-provided service before startup; all plugins in this application can resolve it. */
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
        if (value == null) properties.remove(key);
        else properties.put(key, value);
    }
}
