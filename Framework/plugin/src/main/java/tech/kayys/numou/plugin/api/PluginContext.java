package tech.kayys.numou.plugin.api;

import java.util.Map;
import java.util.Optional;

/**
 * Context provided to a plugin during initialization and lifecycle management.
 */
public interface PluginContext {

    /**
     * Unique ID of the plugin instance this context belongs to.
     */
    String pluginId();

    /**
     * Read a configuration or environment property.
     */
    Optional<String> getProperty(String key);

    /**
     * Read a configuration property with a default fallback.
     */
    default String getProperty(String key, String defaultValue) {
        return getProperty(key).orElse(defaultValue);
    }

    /**
     * Get typed service or shared platform bean.
     */
    <T> Optional<T> getService(Class<T> serviceType);

    /**
     * Register a shared service produced by this plugin.
     */
    <T> void registerService(Class<T> serviceType, T instance);

    /**
     * Read arbitrary context attributes.
     */
    Map<String, Object> attributes();

    /**
     * Set context attribute.
     */
    void setAttribute(String key, Object value);
}
