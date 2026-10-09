package tech.kayys.syirkah.plugin.api;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultPluginContext implements PluginContext {

    private final String pluginId;
    private final Map<String, String> properties = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public DefaultPluginContext(String pluginId) {
        this.pluginId = pluginId;
    }

    public DefaultPluginContext(String pluginId, Map<String, String> initialProperties) {
        this.pluginId = pluginId;
        if (initialProperties != null) {
            properties.putAll(initialProperties);
        }
    }

    @Override
    public String pluginId() {
        return pluginId;
    }

    @Override
    public Optional<String> getProperty(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(properties.get(key));
    }

    public void setProperty(String key, String value) {
        if (key != null && value != null) {
            properties.put(key, value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getService(Class<T> serviceType) {
        if (serviceType == null) return Optional.empty();
        return Optional.ofNullable((T) services.get(serviceType));
    }

    @Override
    public <T> void registerService(Class<T> serviceType, T instance) {
        if (serviceType != null && instance != null) {
            services.put(serviceType, instance);
        }
    }

    @Override
    public Map<String, Object> attributes() {
        return Map.copyOf(attributes);
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (key != null) {
            if (value != null) {
                attributes.put(key, value);
            } else {
                attributes.remove(key);
            }
        }
    }
}
