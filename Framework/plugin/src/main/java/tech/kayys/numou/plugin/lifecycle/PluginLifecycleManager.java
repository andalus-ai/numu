package tech.kayys.numou.plugin.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kayys.numou.plugin.api.DefaultPluginContext;
import tech.kayys.numou.plugin.api.PluginContext;
import tech.kayys.numou.plugin.api.PluginException;
import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.registry.CapabilityRegistry;
import tech.kayys.numou.plugin.registry.PluginRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages plugin lifecycle transitions (initialize, start, stop).
 */
public class PluginLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(PluginLifecycleManager.class);

    private final PluginRegistry pluginRegistry;
    private final CapabilityRegistry capabilityRegistry;
    private final PluginDependencyResolver dependencyResolver;
    private final Map<String, PluginContext> contexts = new ConcurrentHashMap<>();

    public PluginLifecycleManager(PluginRegistry pluginRegistry,
                                  CapabilityRegistry capabilityRegistry,
                                  PluginDependencyResolver dependencyResolver) {
        this.pluginRegistry = pluginRegistry;
        this.capabilityRegistry = capabilityRegistry;
        this.dependencyResolver = dependencyResolver;
    }

    public void initializeAll() {
        List<NumouPlugin> ordered = dependencyResolver.resolveOrder(pluginRegistry.all());
        for (NumouPlugin plugin : ordered) {
            initializePlugin(plugin);
        }
    }

    public void startAll() {
        List<NumouPlugin> ordered = dependencyResolver.resolveOrder(pluginRegistry.all());
        for (NumouPlugin plugin : ordered) {
            startPlugin(plugin);
        }
    }

    public void stopAll() {
        // Stop in reverse dependency order
        List<NumouPlugin> ordered = dependencyResolver.resolveOrder(pluginRegistry.all());
        for (int i = ordered.size() - 1; i >= 0; i--) {
            stopPlugin(ordered.get(i));
        }
    }

    public void initializePlugin(NumouPlugin plugin) {
        PluginContext ctx = contexts.computeIfAbsent(plugin.id(), DefaultPluginContext::new);
        try {
            log.info("Initializing plugin: {}", plugin.id());
            plugin.initialize(ctx);

            // Register capabilities
            plugin.capabilities().forEach(cap -> capabilityRegistry.register(cap, plugin));
        } catch (Exception e) {
            throw new PluginException("Failed to initialize plugin: " + plugin.id(), e);
        }
    }

    public void startPlugin(NumouPlugin plugin) {
        try {
            log.info("Starting plugin: {}", plugin.id());
            plugin.start();
        } catch (Exception e) {
            throw new PluginException("Failed to start plugin: " + plugin.id(), e);
        }
    }

    public void stopPlugin(NumouPlugin plugin) {
        try {
            log.info("Stopping plugin: {}", plugin.id());
            plugin.stop();
            plugin.capabilities().forEach(cap -> capabilityRegistry.unregister(cap, plugin.id()));
        } catch (Exception e) {
            log.error("Error stopping plugin: " + plugin.id(), e);
        }
    }

    public PluginContext getContext(String pluginId) {
        return contexts.get(pluginId);
    }
}
