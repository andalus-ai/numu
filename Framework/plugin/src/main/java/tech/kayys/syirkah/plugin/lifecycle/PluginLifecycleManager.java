package tech.kayys.syirkah.plugin.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kayys.syirkah.plugin.api.DefaultPluginContext;
import tech.kayys.syirkah.plugin.api.PluginContext;
import tech.kayys.syirkah.plugin.api.PluginException;
import tech.kayys.syirkah.plugin.api.SyirkahPlugin;
import tech.kayys.syirkah.plugin.registry.CapabilityRegistry;
import tech.kayys.syirkah.plugin.registry.PluginRegistry;

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
        List<SyirkahPlugin> ordered = dependencyResolver.resolveOrder(pluginRegistry.all());
        for (SyirkahPlugin plugin : ordered) {
            initializePlugin(plugin);
        }
    }

    public void startAll() {
        List<SyirkahPlugin> ordered = dependencyResolver.resolveOrder(pluginRegistry.all());
        for (SyirkahPlugin plugin : ordered) {
            startPlugin(plugin);
        }
    }

    public void stopAll() {
        // Stop in reverse dependency order
        List<SyirkahPlugin> ordered = dependencyResolver.resolveOrder(pluginRegistry.all());
        for (int i = ordered.size() - 1; i >= 0; i--) {
            stopPlugin(ordered.get(i));
        }
    }

    public void initializePlugin(SyirkahPlugin plugin) {
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

    public void startPlugin(SyirkahPlugin plugin) {
        try {
            log.info("Starting plugin: {}", plugin.id());
            plugin.start();
        } catch (Exception e) {
            throw new PluginException("Failed to start plugin: " + plugin.id(), e);
        }
    }

    public void stopPlugin(SyirkahPlugin plugin) {
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
